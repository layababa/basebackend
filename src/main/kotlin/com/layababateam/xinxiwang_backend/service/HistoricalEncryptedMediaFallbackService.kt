package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import com.layababateam.xinxiwang_backend.model.ContentType
import com.layababateam.xinxiwang_backend.model.MediaObject
import com.layababateam.xinxiwang_backend.repository.MediaObjectRepository
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Duration
import java.util.Optional

@Service
class HistoricalEncryptedMediaFallbackService(
    private val ossService: OssService,
    private val mediaKeyRegistry: MediaKeyRegistry,
    private val endpointResolver: MediaEndpointResolver,
    private val mediaObjectRepository: MediaObjectRepository,
    private val objectMapper: ObjectMapper,
) {
    private val mediaObjectCache = Caffeine.newBuilder()
        .maximumSize(20_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build<String, Optional<MediaObject>> { mediaId ->
            Optional.ofNullable(mediaObjectRepository.findFirstByMediaId(mediaId))
        }

    private val existsCache = Caffeine.newBuilder()
        .maximumSize(20_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build<String, Boolean> { key -> ossService.objectExists(key) }

    fun apply(content: String, contentType: Int, sourceContent: String = content): String {
        if (contentType !in mediaContentTypes || content.isBlank()) return content
        val raw = runCatching { objectMapper.readValue(content, Any::class.java) }.getOrNull()
            ?: return content
        val sourcePayload = runCatching { objectMapper.readValue(sourceContent, Map::class.java) }.getOrNull()
        val repaired = when (raw) {
            is Map<*, *> -> repairPayload(raw, contentType, sourcePayload)
            is List<*> -> raw.map { if (it is Map<*, *>) repairPayload(it, contentType, sourcePayload) else it }
            else -> raw
        }
        return if (repaired == raw) content else objectMapper.writeValueAsString(repaired)
    }

    private fun repairPayload(
        payload: Map<*, *>,
        contentType: Int,
        sourcePayload: Map<*, *>?,
    ): Map<String, Any?> {
        val out = payload.entries.associate { it.key.toString() to it.value }.toMutableMap()
        if (booleanValue(out["encryptedFallback"])) return out
        if (out["encrypted"] is Map<*, *> && isEncryptedObjectUrl(stringValue(out["url"]))) return out

        val primary = firstMediaObject(out, primaryUrlKeys, contentType) ?: return out
        mediaObject(primary.mediaId)?.let { indexed ->
            val indexedEncryptedKey = indexed.ossKey.takeIf { it.startsWith("encrypted/") } ?: return out
            if (!indexed.plainOssKey.isNullOrBlank()) return out
            return applyEncryptedFallback(
                payload = out,
                mediaId = primary.mediaId,
                encryptedKey = indexedEncryptedKey,
                metadataThumbOssKey = indexed.thumbOssKey,
                thumbnailObjectKey = indexed.plainThumbOssKey ?: indexed.thumbOssKey,
                cipherThumbOssKey = indexed.thumbOssKey,
                keyId = indexed.keyId,
                alg = indexed.alg,
            )
        }

        val sourceEncrypted = encryptedSource(sourcePayload, primary) ?: return out

        if (objectExists(primary.ossKey)) return out
        if (!objectExists(sourceEncrypted.ossKey)) return out

        val thumb = firstMediaObject(out, thumbnailUrlKeys, contentType)
        val cipherThumbOssKey = sourceEncrypted.thumbOssKey?.takeIf { objectExists(it) }
        val thumbnailObjectKey = when {
            thumb != null && objectExists(thumb.ossKey) -> thumb.ossKey
            thumb != null || contentType in mediaTypesWithThumbnails -> cipherThumbOssKey
            else -> null
        }

        return applyEncryptedFallback(
            payload = out,
            mediaId = primary.mediaId,
            encryptedKey = sourceEncrypted.ossKey,
            metadataThumbOssKey = sourceEncrypted.thumbOssKey,
            thumbnailObjectKey = thumbnailObjectKey,
            cipherThumbOssKey = cipherThumbOssKey,
            keyId = sourceEncrypted.keyId,
            alg = sourceEncrypted.alg,
        )
    }

    private fun applyEncryptedFallback(
        payload: MutableMap<String, Any?>,
        mediaId: String,
        encryptedKey: String,
        metadataThumbOssKey: String?,
        thumbnailObjectKey: String?,
        cipherThumbOssKey: String?,
        keyId: String,
        alg: String,
    ): Map<String, Any?> {
        val encryptedUrl = publicUrl(encryptedKey)
        val thumbnailUrl = thumbnailObjectKey?.let { publicUrl(it) }
        val cipherThumbUrl = cipherThumbOssKey
            ?.let { publicUrl(it) }

        payload["uploadMode"] = "oss-encrypted-direct-fallback"
        payload["encryptedFallback"] = true
        payload["url"] = encryptedUrl
        payload["fileUrl"] = encryptedUrl
        payload["cipherUrl"] = encryptedUrl
        if (thumbnailUrl != null) {
            payload["thumbnailUrl"] = thumbnailUrl
            payload["thumbnail"] = thumbnailUrl
            if (cipherThumbUrl != null) {
                payload["cipherThumbUrl"] = cipherThumbUrl
            } else {
                payload.remove("cipherThumbUrl")
            }
        } else {
            payload.remove("thumbnailUrl")
            payload.remove("thumbnail")
            payload.remove("cipherThumbUrl")
        }
        payload.remove("plainUrl")
        payload.remove("plainThumbnailUrl")
        payload.remove("proxyUrl")
        payload.remove("proxyThumbUrl")

        payload["encrypted"] = mapOf(
            "v" to 1,
            "alg" to alg,
            "keyId" to keyId,
            "mediaId" to mediaId,
            "ossKey" to encryptedKey,
            "thumbOssKey" to metadataThumbOssKey,
            "cipherUrl" to encryptedUrl,
            "cipherThumbUrl" to cipherThumbUrl,
        ).filterValues { it != null }

        return payload
    }

    private fun encryptedSource(sourcePayload: Map<*, *>?, primary: PlainMediaObject): EncryptedSource? {
        val encrypted = (sourcePayload?.get("encrypted") as? Map<*, *>)?.entries
            ?.associate { it.key.toString() to it.value }
            ?: return null
        val mediaId = stringValue(encrypted["mediaId"]) ?: primary.mediaId
        if (mediaId != primary.mediaId) return null

        val ossKey = stringValue(encrypted["ossKey"])
            ?.takeIf { it.startsWith("encrypted/") }
            ?: encryptedObjectKeyFromUrl(stringValue(encrypted["cipherUrl"]))
            ?: encryptedObjectKeyFromUrl(stringValue(encrypted["url"]))
            ?: return null
        val thumbOssKey = stringValue(encrypted["thumbOssKey"])
            ?.takeIf { it.startsWith("encrypted/") }
            ?: encryptedObjectKeyFromUrl(stringValue(encrypted["cipherThumbUrl"]))

        return EncryptedSource(
            ossKey = ossKey,
            thumbOssKey = thumbOssKey,
            keyId = stringValue(encrypted["keyId"])?.takeIf { it.isNotBlank() }
                ?: mediaKeyRegistry.currentKeyId(),
            alg = stringValue(encrypted["alg"])?.takeIf { it.isNotBlank() }
                ?: "AES-256-GCM",
        )
    }

    private fun firstMediaObject(
        payload: Map<String, Any?>,
        keys: List<String>,
        contentType: Int,
    ): PlainMediaObject? {
        return keys.firstNotNullOfOrNull { key ->
            val url = payload[key] as? String ?: return@firstNotNullOfOrNull null
            plainMediaObjectFromUrl(url, contentType)
        }
    }

    private fun plainMediaObjectFromUrl(url: String, contentType: Int): PlainMediaObject? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in acceptedPublicHosts()) return null

        val parts = uri.rawPath.orEmpty().trimStart('/').split('/').filter { it.isNotBlank() }
        if (parts.firstOrNull() == "encrypted") return null
        val categoryForType = categoryFor(contentType)
        val objectKey = parts.joinToString("/")
        val category: String
        val fileName: String

        if (parts.size == 2) {
            category = parts[0]
            fileName = parts[1]
        } else if (parts.size == 3 && parts[0] == "thumbnails") {
            category = parts[1]
            fileName = parts[2]
        } else {
            return null
        }

        if (categoryForType != null && category != categoryForType) return null
        val mediaId = fileName.substringBeforeLast('.', "").takeIf { it.isNotBlank() } ?: return null
        return PlainMediaObject(
            ossKey = objectKey,
            category = category,
            mediaId = mediaId,
        )
    }

    private fun encryptedObjectKeyFromUrl(url: String?): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in acceptedPublicHosts()) return null
        val objectKey = uri.rawPath.orEmpty().trimStart('/')
        return objectKey.takeIf { it.startsWith("encrypted/") }
    }

    private fun acceptedPublicHosts(): Set<String> {
        val configuredHost = runCatching { URI(endpointResolver.currentOssPublicEndpoint()).host?.lowercase() }
            .getOrNull()
        val canonicalHost = URI(MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT).host.lowercase()
        return setOfNotNull(configuredHost, canonicalHost)
    }

    private fun objectExists(ossKey: String): Boolean = existsCache.get(ossKey) == true

    private fun mediaObject(mediaId: String): MediaObject? = mediaObjectCache.get(mediaId)?.orElse(null)

    private fun publicUrl(ossKey: String): String {
        return "${endpointResolver.currentOssPublicEndpoint().trimEnd('/')}/${ossKey.trimStart('/')}"
    }

    private fun stringValue(value: Any?): String? = value as? String

    private fun booleanValue(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun isEncryptedObjectUrl(url: String?): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.path.split('/').filter { it.isNotBlank() }.firstOrNull() == "encrypted"
    }

    private fun categoryFor(contentType: Int): String? {
        return when (contentType) {
            ContentType.IMAGE.value -> "images"
            ContentType.VOICE.value -> "audio"
            ContentType.VIDEO.value -> "videos"
            ContentType.FILE.value -> "files"
            else -> null
        }
    }

    private data class PlainMediaObject(
        val ossKey: String,
        val category: String,
        val mediaId: String,
    )

    private data class EncryptedSource(
        val ossKey: String,
        val thumbOssKey: String?,
        val keyId: String,
        val alg: String,
    )

    private companion object {
        private val mediaContentTypes = setOf(
            ContentType.IMAGE.value,
            ContentType.VOICE.value,
            ContentType.VIDEO.value,
            ContentType.FILE.value,
        )
        private val mediaTypesWithThumbnails = setOf(
            ContentType.IMAGE.value,
            ContentType.VIDEO.value,
        )
        private val primaryUrlKeys = listOf("plainUrl", "url", "fileUrl", "videoUrl", "downloadUrl", "originalUrl")
        private val thumbnailUrlKeys = listOf("plainThumbnailUrl", "thumbnailUrl", "thumbnail")
    }
}
