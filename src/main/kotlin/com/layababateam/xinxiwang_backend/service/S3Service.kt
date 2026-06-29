package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Deprecated("Replaced by OssService. Delete after OSS stability confirmed.")
@Service
@ConditionalOnProperty(
    name = ["rentmsg.legacy.s3.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class S3Service(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val s3DownloadPresigner: S3Presigner,
    @Value("\${media.bucket_general_name}") private val bucketName: String,
    @Value("\${media.bucket_general_domain}") private val bucketDomain: String,
    @Value("\${oss.debug-log-prefix:debug-logs/}") private val debugLogPrefix: String,
) {
    private val log = LoggerFactory.getLogger(S3Service::class.java)

    companion object {
        private val SIZE_LIMITS = mapOf(
            "avatars" to 2L * 1024 * 1024,
            "images" to 10L * 1024 * 1024,
            "videos" to 20L * 1024 * 1024 * 1024,
            "audio" to 10L * 1024 * 1024,
            "files" to 20L * 1024 * 1024 * 1024,
            "stickers" to 10L * 1024 * 1024
        )
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
        private const val MULTIPART_THRESHOLD = 100L * 1024 * 1024 // 100MB
        private const val PART_SIZE = 10L * 1024 * 1024 // 10MB
        private const val MIN_PART_SIZE = 5L * 1024 * 1024
        private const val DEFAULT_PART_SIZE = 10L * 1024 * 1024
        private const val PRESIGN_DURATION_MINUTES = 60L
    }

    // ─── Upload (auto single/multipart to S3) ───────────────────────────

    @CircuitBreaker(name = "s3", fallbackMethod = "uploadFallback")
    fun uploadFile(file: MultipartFile, category: String = "images"): String {
        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast('.', "").lowercase()

        if (extension.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "文件必须有扩展名")
        }

        val maxSize = SIZE_LIMITS[category] ?: SIZE_LIMITS["files"]!!
        if (file.size > maxSize) {
            throw BusinessException(
                ErrorCode.INVALID_PARAM,
                "文件大小 ${file.size / (1024 * 1024)}MB 超过 ${category} 类别限制 ${maxSize / (1024 * 1024)}MB"
            )
        }

        val dir = resolveDir(category)
        val fileId = UUID.randomUUID().toString()
        val key = "$dir/$fileId.$extension"
        val contentType = file.contentType ?: "application/octet-stream"

        // 一次性读取文件字节，避免流被重复读取导致 mark/reset 异常 (GlitchTip #4759)
        val fileBytes = file.bytes

        if (file.size > MULTIPART_THRESHOLD) {
            uploadMultipart(ByteArrayInputStream(fileBytes), key, contentType)
        } else {
            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build()
            s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes))
        }

        if (extension in IMAGE_EXTENSIONS) {
            uploadThumbnail(fileBytes, dir, fileId, extension)
        }

        return "$bucketDomain/$key"
    }

    // ─── Server-side Multipart Upload to S3 ─────────────────────────────

    private fun uploadMultipart(inputStream: InputStream, key: String, contentType: String) {
        val createRequest = CreateMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build()

        val createResponse = s3Client.createMultipartUpload(createRequest)
        val uploadId = createResponse.uploadId()

        try {
            val completedParts = mutableListOf<CompletedPart>()
            var partNumber = 1
            val buffer = ByteArray(PART_SIZE.toInt())

            while (true) {
                var bytesRead = 0
                while (bytesRead < PART_SIZE) {
                    val read = inputStream.read(buffer, bytesRead, (PART_SIZE - bytesRead).toInt())
                    if (read == -1) break
                    bytesRead += read
                }
                if (bytesRead == 0) break

                val uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .contentLength(bytesRead.toLong())
                    .build()

                val response = s3Client.uploadPart(
                    uploadPartRequest,
                    RequestBody.fromBytes(buffer.copyOf(bytesRead))
                )

                completedParts.add(
                    CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(response.eTag())
                        .build()
                )

                log.debug("Uploaded part {} for key={}, size={}", partNumber, key, bytesRead)
                partNumber++
                if (bytesRead < PART_SIZE) break
            }

            val completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(
                    CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build()
                )
                .build()

            s3Client.completeMultipartUpload(completeRequest)
            log.info("Completed multipart upload: key={}, parts={}", key, completedParts.size)
        } catch (e: Exception) {
            try {
                s3Client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .uploadId(uploadId)
                        .build()
                )
            } catch (abortEx: Exception) {
                log.warn("Failed to abort multipart upload: key={}", key, abortEx)
            }
            throw e
        }
    }

    // ─── Presigned Multipart Upload (client-side large files) ─────────

    data class MultipartInitResult(
        val uploadId: String,
        val key: String,
        val presignedUrls: List<String>
    )

    fun initiateMultipartUpload(
        extension: String,
        category: String,
        contentType: String,
        fileSize: Long,
        partSize: Long = DEFAULT_PART_SIZE
    ): MultipartInitResult {
        require(extension.isNotEmpty()) { "File must have an extension" }

        val maxSize = SIZE_LIMITS[category] ?: SIZE_LIMITS["files"]!!
        require(fileSize <= maxSize) {
            "File size $fileSize exceeds limit ${maxSize / (1024 * 1024)}MB for category '$category'"
        }

        val effectivePartSize = maxOf(partSize, MIN_PART_SIZE)
        val dir = resolveDir(category)
        val fileId = UUID.randomUUID().toString()
        val key = "$dir/$fileId.$extension"

        val createRequest = CreateMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build()

        val createResponse = s3Client.createMultipartUpload(createRequest)
        val uploadId = createResponse.uploadId()

        val partCount = ((fileSize + effectivePartSize - 1) / effectivePartSize).toInt()
        val presignedUrls = (1..partCount).map { partNumber ->
            presignUploadPart(key, uploadId, partNumber)
        }

        log.info("Initiated multipart upload: key=$key, uploadId=$uploadId, parts=$partCount, partSize=$effectivePartSize")
        return MultipartInitResult(uploadId = uploadId, key = key, presignedUrls = presignedUrls)
    }

    fun completeMultipartUpload(
        key: String,
        uploadId: String,
        parts: List<CompletedPart>
    ): String {
        val completedUpload = CompletedMultipartUpload.builder()
            .parts(parts)
            .build()

        val request = CompleteMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(key)
            .uploadId(uploadId)
            .multipartUpload(completedUpload)
            .build()

        s3Client.completeMultipartUpload(request)
        log.info("Completed multipart upload: key=$key, uploadId=$uploadId")
        return "$bucketDomain/$key"
    }

    fun abortMultipartUpload(key: String, uploadId: String) {
        val request = AbortMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(key)
            .uploadId(uploadId)
            .build()

        s3Client.abortMultipartUpload(request)
        log.info("Aborted multipart upload: key=$key, uploadId=$uploadId")
    }

    // ─── Presigned PUT (small-file direct upload) ───────────────────────

    data class PresignedUploadResult(
        val putUrl: String,
        val key: String,
        val fileUrl: String,
        val thumbnailPutUrl: String? = null,
        val thumbnailUrl: String? = null
    )

    fun presignPutObject(extension: String, category: String, contentType: String): PresignedUploadResult {
        require(extension.isNotEmpty()) { "File must have an extension" }

        val dir = resolveDir(category)
        val fileId = UUID.randomUUID().toString()
        val key = "$dir/$fileId.$extension"

        val putUrl = presignPut(key, contentType)
        val fileUrl = "$bucketDomain/$key"

        var thumbnailPutUrl: String? = null
        var thumbnailUrl: String? = null
        if (extension.lowercase() in IMAGE_EXTENSIONS) {
            val outputFormat = when (extension.lowercase()) {
                "gif", "webp" -> "png"
                else -> extension.lowercase()
            }
            val thumbnailKey = "thumbnails/$dir/$fileId.$outputFormat"
            thumbnailPutUrl = presignPut(thumbnailKey, "image/$outputFormat")
            thumbnailUrl = "$bucketDomain/$thumbnailKey"
        }

        log.info("Presigned PUT: key=$key")
        return PresignedUploadResult(putUrl, key, fileUrl, thumbnailPutUrl, thumbnailUrl)
    }

    private fun presignPut(key: String, contentType: String): String {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PRESIGN_DURATION_MINUTES))
            .putObjectRequest(putObjectRequest)
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toString()
    }

    // ─── Thumbnail ──────────────────────────────────────────────────────

    private fun uploadThumbnail(fileBytes: ByteArray, dir: String, fileId: String, extension: String) {
        try {
            val outputFormat = when (extension) {
                "gif", "webp" -> "png"
                else -> extension
            }
            val thumbnailKey = "thumbnails/$dir/$fileId.$outputFormat"
            val baos = ByteArrayOutputStream()
            // 使用字节数组创建流，避免流重复读取问题 (GlitchTip #4759)
            // 不支持的图片格式会被 catch 捕获并跳过 (GlitchTip #4659)
            Thumbnails.of(ByteArrayInputStream(fileBytes))
                .size(200, 200)
                .outputFormat(outputFormat)
                .toOutputStream(baos)
            val thumbnailBytes = baos.toByteArray()

            val putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(thumbnailKey)
                .contentType("image/$outputFormat")
                .build()

            s3Client.putObject(putRequest, RequestBody.fromBytes(thumbnailBytes))
        } catch (e: Exception) {
            log.warn("Failed to generate thumbnail for $fileId.$extension, skipping thumbnail", e)
        }
    }

    fun generateThumbnailUrl(originalUrl: String, width: Int = 200, height: Int = 200): String {
        if (!originalUrl.contains(bucketDomain)) return originalUrl
        val ext = originalUrl.substringAfterLast('.', "").lowercase()
        if (ext !in IMAGE_EXTENSIONS) return originalUrl
        val key = originalUrl.removePrefix("$bucketDomain/")
        val dir = key.substringBeforeLast('/')
        val fileId = key.substringAfterLast('/').substringBeforeLast('.')
        val outputFormat = when (ext) {
            "gif", "webp" -> "png"
            else -> ext
        }
        return "$bucketDomain/thumbnails/$dir/$fileId.$outputFormat"
    }

    fun generateAvatarUrl(originalUrl: String, size: Int = 100): String {
        return generateThumbnailUrl(originalUrl, size, size)
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun resolveDir(category: String): String = when (category) {
        "avatars", "images", "videos", "audio", "files", "stickers" -> category
        else -> "files"
    }

    private fun presignUploadPart(key: String, uploadId: String, partNumber: Int): String {
        val uploadPartRequest = UploadPartRequest.builder()
            .bucket(bucketName)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .build()

        val presignRequest = UploadPartPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PRESIGN_DURATION_MINUTES))
            .uploadPartRequest(uploadPartRequest)
            .build()

        return s3Presigner.presignUploadPart(presignRequest).url().toString()
    }

    // ─── Debug log（私有 prefix + 預簽名 GET）─────────────────────────

    data class DebugLogUploadResult(val objectKey: String, val fileSize: Long)

    private val debugLogDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"))

    fun uploadDebugLog(file: File, userId: String, requestId: String?): DebugLogUploadResult {
        val date = debugLogDateFormatter.format(java.time.Instant.now())
        // 追加 millis 后缀避免 requestId 重试上传时 silent 覆盖（B-I7）
        val baseName = requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val name = "$baseName-${System.currentTimeMillis()}.tar.gz"
        val prefix = debugLogPrefix.trimEnd('/')
        val key = "$prefix/$userId/$date/$name"

        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("application/gzip")
            .build()
        s3Client.putObject(putRequest, RequestBody.fromFile(file))
        log.info("Uploaded debug log: key={}, size={}", key, file.length())
        return DebugLogUploadResult(objectKey = key, fileSize = file.length())
    }

    fun presignGetObject(key: String, ttlSeconds: Long, downloadFilename: String? = null): String {
        val getObjectRequestBuilder = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
        // 设置 Content-Disposition: attachment，强制浏览器下载而非尝试渲染
        if (downloadFilename != null) {
            getObjectRequestBuilder.responseContentDisposition(
                "attachment; filename=\"$downloadFilename\""
            )
        }
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(ttlSeconds))
            .getObjectRequest(getObjectRequestBuilder.build())
            .build()
        return s3DownloadPresigner.presignGetObject(presignRequest).url().toString()
    }

    @Suppress("unused")
    private fun uploadFallback(file: MultipartFile, category: String, ex: Throwable): String {
        log.error("S3 upload failed, circuit open", ex)
        throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "文件上传服务暂不可用")
    }
}
