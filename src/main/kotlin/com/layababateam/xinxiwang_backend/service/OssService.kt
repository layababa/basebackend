package com.layababateam.xinxiwang_backend.service

import com.aliyun.oss.HttpMethod
import com.aliyun.oss.OSS
import com.aliyun.oss.model.CannedAccessControlList
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.aliyun.oss.model.ObjectMetadata
import com.aliyun.oss.model.PutObjectRequest
import com.aliyun.oss.model.ResponseHeaderOverrides
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

/**
 * Thin wrapper around the Aliyun OSS Java SDK that hides bucket-name and
 * client-selection details from callers.
 *
 * Two clients are injected:
 *
 *  - `ossClientInternal` — used for server-side writes such as multipart
 *    fallback, stickers, and debug logs.
 *  - `ossClientPublic`  — used to mint browser/client GET URLs.
 *  - `ossClientUpload`  — used to mint direct client PUT URLs.
 */
@Service
class OssService(
    @Qualifier("ossClientInternal") private val ossInternal: OSS,
    @Qualifier("ossClientPublic") private val ossPublic: OSS,
    @Qualifier("ossClientUpload") private val ossUpload: OSS,
    @Value("\${aliyun.oss.bucket-encrypted}") private val bucket: String,
    @Value("\${oss.debug-log-prefix:debug-logs/}") private val debugLogPrefix: String = "debug-logs/",
) {
    fun bucketName(): String = bucket

    fun objectExists(ossKey: String): Boolean {
        return runCatching { ossInternal.doesObjectExist(bucket, ossKey) }
            .getOrDefault(false)
    }

    /**
     * Pre-sign a PUT URL. Current clients use this to upload media directly
     * to OSS without the server ever touching the bytes.  Returned URL is
     * rooted at the upload endpoint so mobile clients do not need to reach the
     * raw regional OSS host.
     */
    fun presignPut(ossKey: String, mime: String, expireMin: Long = 30L): String {
        val expiration = Date(System.currentTimeMillis() + expireMin * 60_000)
        val req = GeneratePresignedUrlRequest(bucket, ossKey, HttpMethod.PUT)
        req.expiration = expiration
        req.contentType = mime
        return ossUpload.generatePresignedUrl(req).toString()
    }

    /**
     * Pre-sign a GET URL. Legacy `/api/media/...` compatibility uses this for
     * a 302 redirect so media bytes are downloaded from OSS, not the backend.
     */
    fun presignGet(ossKey: String, expireMin: Long = 10L): String {
        val expiration = Date(System.currentTimeMillis() + expireMin * 60_000)
        val req = GeneratePresignedUrlRequest(bucket, ossKey, HttpMethod.GET)
        req.expiration = expiration
        return ossPublic.generatePresignedUrl(req).toString()
    }

    /**
     * Pre-sign a GET URL with optional Content-Disposition override.  Admin
     * debug-log download uses this so the browser downloads the file instead
     * of rendering it inline.  `expireMin` is in minutes.
     */
    fun presignGet(ossKey: String, expireMin: Long, contentDisposition: String?): String {
        val expiration = Date(System.currentTimeMillis() + expireMin * 60_000)
        val req = GeneratePresignedUrlRequest(bucket, ossKey, HttpMethod.GET)
        req.expiration = expiration
        contentDisposition?.let {
            req.responseHeaders = ResponseHeaderOverrides().apply {
                this.contentDisposition = it
            }
        }
        return ossPublic.generatePresignedUrl(req).toString()
    }

    /**
     * Server-side plaintext write used by the low-traffic legacy multipart
     * fallback and non-chat assets. Current chat media uses presigned PUT.
     */
    fun putObject(ossKey: String, bytes: ByteArray, mime: String) {
        val meta = ObjectMetadata().apply {
            contentLength = bytes.size.toLong()
            contentType = mime
        }
        ossInternal.putObject(PutObjectRequest(bucket, ossKey, ByteArrayInputStream(bytes), meta))
    }

    fun putPublicObject(ossKey: String, bytes: ByteArray, mime: String) {
        val meta = ObjectMetadata().apply {
            contentLength = bytes.size.toLong()
            contentType = mime
            setObjectAcl(CannedAccessControlList.PublicRead)
        }
        ossInternal.putObject(PutObjectRequest(bucket, ossKey, ByteArrayInputStream(bytes), meta))
    }

    // ── Debug-log upload (reuse rentmsg's S3Service.uploadDebugLog key layout) ──

    data class DebugLogUploadResult(val objectKey: String, val fileSize: Long)

    private val debugLogDateFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"))

    /**
     * Upload a tar.gz debug log to the private debug-log prefix on the OSS
     * bucket.  Object-key layout mirrors [S3Service.uploadDebugLog] so records
     * already stored with the S3 keys remain addressable after switching.
     */
    fun uploadDebugLog(file: File, userId: String, requestId: String?): DebugLogUploadResult {
        val date = debugLogDateFormatter.format(java.time.Instant.now())
        val baseName = requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val name = "$baseName-${System.currentTimeMillis()}.tar.gz"
        val prefix = debugLogPrefix.trimEnd('/')
        val objectKey = "$prefix/$userId/$date/$name"
        val fileSize = file.length()
        val meta = ObjectMetadata().apply {
            contentLength = fileSize
            contentType = "application/gzip"
        }
        file.inputStream().use { ins ->
            ossInternal.putObject(PutObjectRequest(bucket, objectKey, ins, meta))
        }
        return DebugLogUploadResult(objectKey, fileSize)
    }
}
