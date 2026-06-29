package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.extensions.MediaSentry
import com.layababateam.xinxiwang_backend.model.MediaObject
import com.layababateam.xinxiwang_backend.repository.DebugLogReportRepository
import com.layababateam.xinxiwang_backend.repository.MediaObjectRepository
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * OSS-backed upload implementation for the SDK upload routes.
 *
 * Current clients upload media through `/api/upload/direct/presign` and PUT
 * bytes directly to OSS. Historical encrypted-upload clients keep calling
 * `/api/upload/encrypted/presign` + `/finalize`; those endpoints now only mint
 * OSS PUT URLs and create redirect metadata for old `/api/media/...` URLs.
 *
 * This service never encrypts, decrypts, or streams media bytes.
 */
@Service
class OssUploadService(
    private val ossService: OssService,
    private val endpointResolver: MediaEndpointResolver,
    private val mediaKeyRegistry: MediaKeyRegistry,
    private val mediaProxyTokenService: MediaProxyTokenService,
    private val mediaObjectRepository: MediaObjectRepository,
    private val debugLogReportRepository: DebugLogReportRepository,
    @Value("\${media.proxy.public-base:\${rentmsg.media.proxy.public-base:}}") private val proxyPublicBase: String,
    @Value("\${media.encrypted-plain-ready-wait-ms:\${rentmsg.media.encrypted-plain-ready-wait-ms:8000}}")
    private val encryptedPlainReadyWaitMs: Long = 8_000L,
) : UploadPort {

    private val log = LoggerFactory.getLogger(OssUploadService::class.java)

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
        private val IMAGE_LIKE_CATEGORIES = setOf("avatars", "stickers", "moment_images", "feedback_images", "images")

        private val PLAINTEXT_CATEGORIES = setOf(
            "stickers", "avatars", "moment_images", "moment_videos",
            "feedback_images", "images", "videos", "audio", "files",
        )

        private const val DEBUG_LOG_MAX_SIZE = 100L * 1024 * 1024
        private const val THUMBNAIL_EDGE = 200
        private const val THUMBNAIL_EXT = "jpg"
        private const val UNKNOWN_OWNER = "unknown"

        private val SIZE_LIMITS = mapOf(
            "avatars" to 5L * 1024 * 1024,
            "stickers" to 5L * 1024 * 1024,
            "moment_images" to 15L * 1024 * 1024,
            "moment_videos" to 500L * 1024 * 1024,
            "feedback_images" to 10L * 1024 * 1024,
            "images" to 15L * 1024 * 1024,
            "videos" to 2L * 1024 * 1024 * 1024,
            "files" to 2L * 1024 * 1024 * 1024,
            "audio" to 50L * 1024 * 1024,
        )
    }

    override fun uploadFile(
        file: MultipartFile,
        category: String,
        requestId: String?,
        userId: String?,
    ): UploadResponse {
        val requestedCategory = normalizeCategory(category)
        if (requestedCategory == "debug_log") {
            return handleDebugLog(file, requestId, userId)
        }

        val maxSize = SIZE_LIMITS[requestedCategory] ?: SIZE_LIMITS["files"]!!
        if (file.size > maxSize) {
            throw BusinessException(
                ErrorCode.INVALID_PARAM,
                "文件大小 ${file.size / (1024 * 1024)}MB 超过 $requestedCategory 类别限制 ${maxSize / (1024 * 1024)}MB",
            )
        }

        val originalName = file.originalFilename ?: ""
        val rawExt = originalName.substringAfterLast('.', "").lowercase()
        if (rawExt.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "文件必须有扩展名")
        }

        if (requestedCategory !in PLAINTEXT_CATEGORIES) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "不支持的上传类别: $requestedCategory")
        }

        return handlePlaintextUpload(
            category = requestedCategory,
            mediaId = UUID.randomUUID().toString().replace("-", ""),
            ext = plaintextExtension(requestedCategory, rawExt),
            mime = file.contentType ?: guessContentType(rawExt),
            plainBytes = file.bytes,
        )
    }

    /**
     * Compatibility endpoint for clients that still encrypt locally before PUT.
     * The bytes now go to Hong Kong OSS, but the object layout stays under
     * `encrypted/...` so ciphertext is never exposed as a plaintext media URL.
     */
    override fun presignEncrypted(body: Map<String, Any>): UploadResponse {
        val extension = stringValue(body["extension"])
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_PARAM, "缺少 extension")
        val category = normalizeCategory(stringValue(body["category"]) ?: "images")
            .takeIf { it in PLAINTEXT_CATEGORIES }
            ?: throw BusinessException(ErrorCode.INVALID_PARAM, "不支持的上传类别")
        val mime = stringValue(body["mime"])
            ?: stringValue(body["contentType"])
            ?: guessContentType(extension)
        val hasThumb = booleanValue(body["hasThumb"]) == true

        val mediaId = UUID.randomUUID().toString().replace("-", "")
        val ossKey = "encrypted/$category/$mediaId.bin"
        val publicEndpoint = endpointResolver.currentOssPublicEndpoint().trimEnd('/')
        MediaSentry.breadcrumb(
            stage = "encrypted_presign",
            mediaType = MediaSentry.mediaTypeFromCategory(category),
            message = "encrypted upload presign",
            data = mapOf(
                "mediaId" to mediaId,
                "category" to category,
                "extension" to extension,
                "mime" to mime,
                "hasThumb" to hasThumb,
                "ossKey" to ossKey,
            ),
        )
        // Old encrypted clients PUT with application/octet-stream. Keep that
        // content type in the signature so all released clients can upload.
        val putUrl = ossService.presignPut(ossKey, "application/octet-stream", 30L)
        val fileUrl = "$publicEndpoint/$ossKey"

        var thumbOssKey: String? = null
        var thumbPutUrl: String? = null
        var thumbUrl: String? = null
        if (hasThumb) {
            thumbOssKey = "encrypted/thumbnails/$category/$mediaId.bin"
            val thumbnailMediaType = if (MediaSentry.mediaTypeFromCategory(category) == "video") {
                "video_thumbnail"
            } else {
                "image_thumbnail"
            }
            MediaSentry.breadcrumb(
                stage = "encrypted_thumbnail_presign",
                mediaType = thumbnailMediaType,
                message = "encrypted upload thumbnail presign",
                data = mapOf(
                    "mediaId" to mediaId,
                    "category" to category,
                    "thumbOssKey" to thumbOssKey,
                ),
            )
            thumbPutUrl = ossService.presignPut(thumbOssKey, "application/octet-stream", 30L)
            thumbUrl = "$publicEndpoint/$thumbOssKey"
        }

        return UploadResponse(
            body = ApiResponse.ok(
                mapOf(
                    "mediaId" to mediaId,
                    "putUrl" to putUrl,
                    "fileUrl" to fileUrl,
                    "url" to fileUrl,
                    "ossKey" to ossKey,
                    "thumbOssKey" to thumbOssKey,
                    "thumbPutUrl" to thumbPutUrl,
                    "thumbnailOssKey" to thumbOssKey,
                    "thumbnailPutUrl" to thumbPutUrl,
                    "thumbnailUrl" to thumbUrl,
                    "cipherUrl" to fileUrl,
                    "cipherThumbUrl" to thumbUrl,
                    "bucket" to ossService.bucketName(),
                    "ossEndpoint" to publicEndpoint,
                    "keyId" to mediaKeyRegistry.currentKeyId(),
                    "category" to category,
                    "mime" to mime,
                    "extension" to extension,
                    "uploadMode" to "oss-encrypted-direct",
                    "encrypted" to true,
                )
            ),
        )
    }

    override fun presignDirectUpload(body: Map<String, Any>): UploadResponse {
        val extension = normalizeDirectExtension(stringValue(body["extension"]) ?: "")
            ?: throw BusinessException(ErrorCode.INVALID_PARAM, "\u7f3a\u5c11 extension")
        val category = normalizeCategory(stringValue(body["category"]) ?: "files")
        val contentType = stringValue(body["contentType"])
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: guessContentType(extension)

        val fileId = UUID.randomUUID().toString()
        val dir = resolveUploadDir(category)
        val ossKey = "$dir/$fileId.$extension"
        MediaSentry.breadcrumb(
            stage = "direct_presign",
            mediaType = MediaSentry.mediaTypeFromCategory(category),
            message = "direct upload presign",
            data = mapOf(
                "category" to category,
                "extension" to extension,
                "contentType" to contentType,
                "fileSize" to numberValue(body["fileSize"]),
                "hasThumb" to booleanValue(body["hasThumb"]),
                "ossKey" to ossKey,
            ),
        )
        val putUrl = ossService.presignPut(ossKey, contentType, 30L)
        val publicEndpoint = endpointResolver.currentOssPublicEndpoint().trimEnd('/')
        val fileUrl = "$publicEndpoint/$ossKey"

        var thumbnailPutUrl: String? = null
        var thumbnailUrl: String? = null
        var thumbnailOssKey: String? = null
        if (booleanValue(body["hasThumb"]) == true) {
            thumbnailOssKey = "thumbnails/$dir/$fileId.$THUMBNAIL_EXT"
            val thumbnailMediaType = if (MediaSentry.mediaTypeFromCategory(category) == "video") {
                "video_thumbnail"
            } else {
                "image_thumbnail"
            }
            MediaSentry.breadcrumb(
                stage = "direct_thumbnail_presign",
                mediaType = thumbnailMediaType,
                message = "direct upload thumbnail presign",
                data = mapOf(
                    "category" to category,
                    "extension" to extension,
                    "thumbnailOssKey" to thumbnailOssKey,
                ),
            )
            thumbnailPutUrl = ossService.presignPut(thumbnailOssKey, "image/jpeg", 30L)
            thumbnailUrl = "$publicEndpoint/$thumbnailOssKey"
        }

        return UploadResponse(
            body = ApiResponse.ok(
                mapOf(
                    "putUrl" to putUrl,
                    "fileUrl" to fileUrl,
                    "url" to fileUrl,
                    "thumbnailPutUrl" to thumbnailPutUrl,
                    "thumbnailUrl" to thumbnailUrl,
                    "ossKey" to ossKey,
                    "thumbnailOssKey" to thumbnailOssKey,
                    "bucket" to ossService.bucketName(),
                    "ossEndpoint" to publicEndpoint,
                    "category" to category,
                    "encrypted" to false,
                    "uploadMode" to "oss-direct",
                ),
            ),
        )
    }

    override fun finalizeEncrypted(
        body: Map<String, Any>,
        userId: String?,
    ): UploadResponse {
        val mediaId = stringValue(body["mediaId"])
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.INVALID_PARAM, "缺少 mediaId")
        val category = normalizeCategory(stringValue(body["category"]) ?: "files")
            .takeIf { it in PLAINTEXT_CATEGORIES }
            ?: throw BusinessException(ErrorCode.INVALID_PARAM, "不支持的上传类别")
        val extension = ((stringValue(body["ext"]) ?: stringValue(body["extension"]))
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?: extensionFromKey(stringValue(body["ossKey"]))
            ?: "bin")
        val ossKey = stringValue(body["ossKey"])
            ?.trim()
            ?.takeIf { isUploadKeyForMedia(it, mediaId, thumbnail = false) }
            ?: throw BusinessException(ErrorCode.INVALID_PARAM, "ossKey 格式非法")
        val thumbOssKey = stringValue(body["thumbOssKey"])
            ?.trim()
            ?.takeIf { isUploadKeyForMedia(it, mediaId, thumbnail = true) }
        if (mediaObjectRepository.existsByMediaId(mediaId)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "mediaId 已存在")
        }

        val publicEndpoint = endpointResolver.currentOssPublicEndpoint().trimEnd('/')
        val fileUrl = "$publicEndpoint/$ossKey"
        val thumbUrl = thumbOssKey?.let { "$publicEndpoint/$it" }
        val mime = stringValue(body["mime"])?.takeIf { it.isNotBlank() }
            ?: guessContentType(extension)
        val plainSize = numberValue(body["plainSize"]) ?: 0L
        val cipherSize = numberValue(body["cipherSize"]) ?: plainSize
        val ownerId = userId ?: UNKNOWN_OWNER
        val storesEncryptedBytes = ossKey.startsWith("encrypted/")
        var plainExtension = plaintextExtension(category, extension)
        var plainOssKey: String? = if (storesEncryptedBytes) {
            decryptedPlainKey(category, mediaId, plainExtension, thumbnail = false)
        } else {
            ossKey
        }
        var plainThumbOssKey: String? = if (storesEncryptedBytes) {
            thumbOssKey?.let { decryptedPlainKey(category, mediaId, THUMBNAIL_EXT, thumbnail = true) }
        } else {
            thumbOssKey
        }
        var encryptedFallback = false
        var encryptedThumbFallback = false
        if (storesEncryptedBytes) {
            val selection = waitForDecryptedPlainObjects(
                mediaId = mediaId,
                category = category,
                plainOssKeyCandidates = decryptedPlainKeyCandidates(category, mediaId, plainExtension),
                plainThumbOssKey = plainThumbOssKey,
            )
            val selectedMainKey = selection.mainKey
            if (selectedMainKey != null) {
                plainOssKey = selectedMainKey
                plainExtension = extensionFromKey(selectedMainKey) ?: plainExtension
            } else {
                if (!ossService.objectExists(ossKey)) {
                    throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "媒体解密结果未就绪，请稍后重试")
                }
                encryptedFallback = true
                plainOssKey = null
            }
            if (thumbOssKey != null) {
                if (selection.thumbKey != null) {
                    plainThumbOssKey = selection.thumbKey
                } else {
                    plainThumbOssKey = null
                    encryptedThumbFallback = ossService.objectExists(thumbOssKey)
                }
            }
        }
        val token = mediaProxyTokenService.sign(mediaId)
        val normalizedProxyBase = proxyPublicBase.trimEnd('/')
        val proxyUrl = "$normalizedProxyBase/api/media/$mediaId/$token.$plainExtension"
        val proxyThumbUrl = if (thumbOssKey != null) {
            "$normalizedProxyBase/api/media/$mediaId/${token}_t.$THUMBNAIL_EXT"
        } else {
            null
        }
        val alg = stringValue(body["alg"])?.takeIf { it.isNotBlank() }
            ?: if (storesEncryptedBytes) "AES-256-GCM" else "OSS-DIRECT"
        val keyId = stringValue(body["keyId"])?.takeIf { it.isNotBlank() }
            ?: mediaKeyRegistry.currentKeyId()
        MediaSentry.breadcrumb(
            stage = "encrypted_finalize",
            mediaType = MediaSentry.mediaTypeFromCategory(category),
            message = "encrypted upload finalize",
            data = mapOf(
                "mediaId" to mediaId,
                "category" to category,
                "extension" to extension,
                "plainExtension" to plainExtension,
                "storesEncryptedBytes" to storesEncryptedBytes,
                "ossKey" to ossKey,
                "thumbOssKey" to thumbOssKey,
                "plainOssKey" to plainOssKey,
                "plainThumbOssKey" to plainThumbOssKey,
                "plainSize" to plainSize,
                "cipherSize" to cipherSize,
                "encryptedFallback" to encryptedFallback,
                "encryptedThumbFallback" to encryptedThumbFallback,
            ),
        )
        val usesEncryptedFallback = encryptedFallback
        val plainUrl = plainOssKey?.let { "$publicEndpoint/$it" }
        val plainThumbUrl = plainThumbOssKey?.let { "$publicEndpoint/$it" }
        val responseFileUrl = if (storesEncryptedBytes) {
            plainUrl ?: fileUrl
        } else {
            fileUrl
        }
        val responseThumbnailUrl = if (storesEncryptedBytes) {
            plainThumbUrl ?: thumbUrl?.takeIf { encryptedFallback && encryptedThumbFallback }
        } else {
            thumbUrl
        }
        val cipherThumbUrl = if (
            thumbOssKey != null &&
            (encryptedThumbFallback || (encryptedFallback && ossService.objectExists(thumbOssKey)))
        ) {
            thumbUrl
        } else {
            null
        }
        val encryptedMetadata = if (storesEncryptedBytes && usesEncryptedFallback) {
            mapOf(
                "v" to 1,
                "alg" to alg,
                "keyId" to keyId,
                "mediaId" to mediaId,
                "ossKey" to ossKey,
                "thumbOssKey" to thumbOssKey,
                "cipherUrl" to fileUrl,
                "cipherThumbUrl" to cipherThumbUrl,
            ).filterValues { it != null }
        } else {
            null
        }

        mediaObjectRepository.save(
            MediaObject(
                mediaId = mediaId,
                ownerId = ownerId,
                conversationId = stringValue(body["conversationId"])?.takeIf { it.isNotBlank() },
                category = category,
                mime = mime,
                ext = plainExtension,
                ossBucket = ossService.bucketName(),
                ossKey = ossKey,
                thumbOssKey = thumbOssKey,
                plainOssKey = plainOssKey,
                plainThumbOssKey = plainThumbOssKey,
                keyId = keyId,
                alg = alg,
                plainSize = plainSize,
                cipherSize = cipherSize,
                thumbPlainSize = numberValue(body["thumbPlainSize"]),
                width = numberValue(body["width"])?.toInt(),
                height = numberValue(body["height"])?.toInt(),
                durationMs = numberValue(body["durationMs"]),
            )
        )

        return UploadResponse(
            body = ApiResponse.ok(
                mapOf(
                    "uploadMode" to when {
                        usesEncryptedFallback -> "oss-encrypted-direct-fallback"
                        storesEncryptedBytes -> "oss-encrypted-direct"
                        else -> "oss-direct"
                    },
                    "encrypted" to (encryptedMetadata ?: storesEncryptedBytes),
                    "encryptedFallback" to usesEncryptedFallback,
                    "mediaId" to mediaId,
                    "category" to category,
                    "extension" to plainExtension,
                    "url" to responseFileUrl,
                    "thumbnailUrl" to responseThumbnailUrl,
                    "proxyUrl" to proxyUrl.takeUnless { encryptedFallback },
                    "proxyThumbUrl" to proxyThumbUrl.takeUnless { encryptedFallback || encryptedThumbFallback },
                    "fileUrl" to responseFileUrl,
                    "cipherUrl" to fileUrl,
                    "cipherThumbUrl" to thumbUrl,
                    "plainUrl" to plainUrl,
                    "plainThumbnailUrl" to plainThumbUrl,
                    "ossEndpoint" to publicEndpoint,
                    "ossKey" to ossKey,
                    "thumbOssKey" to thumbOssKey,
                    "plainOssKey" to plainOssKey,
                    "plainThumbOssKey" to plainThumbOssKey,
                    "keyId" to keyId,
                    "alg" to alg,
                    "token" to token,
                )
            ),
        )
    }

    private fun handleDebugLog(
        file: MultipartFile,
        requestId: String?,
        userId: String?,
    ): UploadResponse {
        val authenticatedUserId = userId
            ?: return UploadResponse(
                status = 401,
                body = ApiResponse.error<Nothing>(ErrorCode.UNAUTHORIZED, "未认证"),
            )
        if (file.size > DEBUG_LOG_MAX_SIZE) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "debug log 文件超过 100MB")
        }
        if (!requestId.isNullOrBlank()) {
            val report = debugLogReportRepository.findById(requestId).orElse(null)
                ?: throw BusinessException(ErrorCode.NOT_FOUND, "debug log 记录不存在")
            if (report.userId != userId) {
                return UploadResponse(
                    status = 403,
                    body = ApiResponse.error<Nothing>(ErrorCode.FORBIDDEN, "requestId 不属于当前用户"),
                )
            }
        }
        val tmp = File.createTempFile("debug-log-", ".tar.gz")
        return try {
            file.transferTo(tmp)
            val result = ossService.uploadDebugLog(tmp, authenticatedUserId, requestId)
            UploadResponse(
                body = ApiResponse.ok(
                    mapOf(
                        "objectKey" to result.objectKey,
                        "fileSize" to result.fileSize,
                    ),
                ),
            )
        } finally {
            tmp.delete()
        }
    }

    private fun handlePlaintextUpload(
        category: String,
        mediaId: String,
        ext: String,
        mime: String,
        plainBytes: ByteArray,
    ): UploadResponse {
        val plainKey = "$category/$mediaId.$ext"
        ossService.putObject(plainKey, plainBytes, mime)
        val publicEndpoint = endpointResolver.currentOssPublicEndpoint().trimEnd('/')
        val data = mutableMapOf<String, String>(
            "url" to "$publicEndpoint/$plainKey",
            "uploadMode" to "legacy-backend-plaintext",
        )

        if (ext in IMAGE_EXTENSIONS) {
            val thumbBytes = buildThumbnailBytes(plainBytes)
            if (thumbBytes != null) {
                val thumbKey = "thumbnails/$category/$mediaId.$THUMBNAIL_EXT"
                ossService.putObject(thumbKey, thumbBytes, "image/jpeg")
                data["thumbnailUrl"] = "$publicEndpoint/$thumbKey"
            }
        }

        return UploadResponse(ApiResponse.ok(data.toMap()))
    }

    private fun normalizeCategory(category: String): String {
        return when (category.trim().lowercase()) {
            "image", "photo", "photos" -> "images"
            "video" -> "videos"
            "file" -> "files"
            "voice", "voices" -> "audio"
            "avatar" -> "avatars"
            "sticker" -> "stickers"
            "moment_image" -> "moment_images"
            "moment_video" -> "moment_videos"
            "feedback_image" -> "feedback_images"
            else -> category.trim().lowercase()
        }
    }

    private fun normalizeDirectExtension(extension: String): String? {
        val value = extension
            .trim()
            .trimStart('.')
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: return null
        return if (value in setOf("jpg", "jpeg")) "jpg" else value
    }

    private fun resolveUploadDir(category: String): String {
        return when (category) {
            "images" -> "images"
            "videos" -> "videos"
            "audio" -> "audio"
            "avatars" -> "avatars"
            "stickers" -> "stickers"
            "moment_images" -> "moment_images"
            "moment_videos" -> "moment_videos"
            "feedback_images" -> "feedback_images"
            else -> "files"
        }
    }

    private fun isUploadKeyForMedia(ossKey: String, mediaId: String, thumbnail: Boolean): Boolean {
        if (ossKey.contains("..")) return false
        if (ossKey.startsWith("/") || ossKey.endsWith("/")) return false
        val segments = ossKey.split('/')
        if (segments.any { it.isBlank() }) return false

        if (segments.firstOrNull() == "encrypted") {
            return if (thumbnail) {
                segments.size >= 4 &&
                    segments[1] == "thumbnails" &&
                    segments[2] in PLAINTEXT_CATEGORIES &&
                    segments.last() == "$mediaId.bin"
            } else {
                segments.size >= 3 &&
                    segments[1] in PLAINTEXT_CATEGORIES &&
                    segments.last() == "$mediaId.bin"
            }
        }

        return if (thumbnail) {
            segments.size == 3 &&
                segments[0] == "thumbnails" &&
                segments[1] in PLAINTEXT_CATEGORIES &&
                segments[2].substringBeforeLast('.', "") == mediaId
        } else {
            segments.size == 2 &&
                segments[0] in PLAINTEXT_CATEGORIES &&
                segments[1].substringBeforeLast('.', "") == mediaId
        }
    }

    private fun decryptedPlainKey(
        category: String,
        mediaId: String,
        extension: String,
        thumbnail: Boolean,
    ): String {
        return if (thumbnail) {
            "thumbnails/$category/$mediaId.$THUMBNAIL_EXT"
        } else {
            "$category/$mediaId.${plaintextExtension(category, extension)}"
        }
    }

    private fun decryptedPlainKeyCandidates(
        category: String,
        mediaId: String,
        extension: String,
    ): List<String> {
        val preferred = plaintextExtension(category, extension)
        val extensions = when {
            category in IMAGE_LIKE_CATEGORIES -> listOf(preferred, "jpg", "jpeg", "png", "gif", "webp", "bin")
            category in setOf("videos", "moment_videos") -> listOf(preferred, "mp4", "mov", "bin")
            category == "audio" -> listOf(preferred, "m4a", "mp3", "aac", "wav", "ogg", "flac", "amr", "bin")
            else -> listOf(
                preferred,
                "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip",
                "jpg", "jpeg", "png", "gif", "webp",
                "mp4", "mov",
                "m4a", "mp3", "aac", "wav", "ogg", "flac", "amr",
                "bin",
            )
        }
        return extensions
            .map { it.trimStart('.').lowercase() }
            .filter { it.isNotBlank() && "/" !in it && "\\" !in it }
            .distinct()
            .map { "$category/$mediaId.$it" }
    }

    private fun plaintextExtension(category: String, extension: String): String {
        val normalized = extension.trimStart('.').lowercase()
        return when {
            category in IMAGE_LIKE_CATEGORIES && normalized in setOf("jpg", "jpeg") -> "jpg"
            category in setOf("videos", "moment_videos") -> if (normalized == "mov") "mov" else "mp4"
            category == "audio" -> when (normalized) {
                "mp3", "aac", "wav", "ogg", "flac", "amr" -> normalized
                else -> "m4a"
            }
            else -> normalized
        }
    }

    private data class DecryptedPlainSelection(
        val mainKey: String?,
        val thumbKey: String?,
    )

    private fun waitForDecryptedPlainObjects(
        mediaId: String,
        category: String,
        plainOssKeyCandidates: List<String>,
        plainThumbOssKey: String?,
    ): DecryptedPlainSelection {
        if (plainOssKeyCandidates.isEmpty()) return DecryptedPlainSelection(null, null)

        fun firstExistingMain(): String? = plainOssKeyCandidates.firstOrNull { ossService.objectExists(it) }
        fun existingThumb(): String? = plainThumbOssKey?.takeIf { ossService.objectExists(it) }

        if (encryptedPlainReadyWaitMs <= 0) {
            return DecryptedPlainSelection(firstExistingMain(), existingThumb())
        }

        var selectedMain: String? = null
        var selectedThumb: String? = null
        val deadline = System.currentTimeMillis() + encryptedPlainReadyWaitMs
        while (System.currentTimeMillis() < deadline) {
            selectedMain = firstExistingMain()
            selectedThumb = existingThumb()
            if (selectedMain != null && (plainThumbOssKey == null || selectedThumb != null)) {
                return DecryptedPlainSelection(selectedMain, selectedThumb)
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            Thread.sleep(minOf(250L, remaining))
        }
        selectedMain = selectedMain ?: firstExistingMain()
        selectedThumb = selectedThumb ?: existingThumb()
        log.warn(
            "Decrypted OSS plain object not ready after {}ms: mainCandidates={}, thumb={}",
            encryptedPlainReadyWaitMs,
            plainOssKeyCandidates,
            plainThumbOssKey,
        )
        MediaSentry.captureSampled(
            stage = "encrypted_plain_not_ready",
            mediaType = MediaSentry.mediaTypeFromCategory(category),
            message = "encrypted upload plain object not ready",
            dedupKeyParts = listOf(mediaId, category),
            data = mapOf(
                "mediaId" to mediaId,
                "category" to category,
                "waitMs" to encryptedPlainReadyWaitMs,
                "plainOssKeyCandidates" to plainOssKeyCandidates,
                "selectedPlainOssKey" to selectedMain,
                "plainThumbOssKey" to plainThumbOssKey,
                "selectedPlainThumbOssKey" to selectedThumb,
            ),
        )
        return DecryptedPlainSelection(selectedMain, selectedThumb)
    }

    private fun extensionFromKey(ossKey: String?): String? {
        val fileName = ossKey?.substringAfterLast('/') ?: return null
        return fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.lowercase()
    }

    private fun numberValue(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun guessContentType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun stringValue(value: Any?): String? = value as? String

    private fun booleanValue(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }

    private fun buildThumbnailBytes(plain: ByteArray): ByteArray? {
        return try {
            ByteArrayInputStream(plain).use { input ->
                val baos = ByteArrayOutputStream()
                Thumbnails.of(input)
                    .size(THUMBNAIL_EDGE, THUMBNAIL_EDGE)
                    .outputFormat(THUMBNAIL_EXT)
                    .toOutputStream(baos)
                baos.toByteArray()
            }
        } catch (e: Exception) {
            log.warn("Thumbnailator failed: {}", e.message)
            null
        }
    }
}
