package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ContentType
import java.net.URI

object MediaContentUrlNormalizer {
    private val mediaContentTypes = setOf(
        ContentType.IMAGE.value,
        ContentType.VOICE.value,
        ContentType.VIDEO.value,
        ContentType.FILE.value,
        ContentType.STICKER.value,
    )

    private val directUrlKeys = setOf(
        "url",
        "fileUrl",
        "plainUrl",
        "thumbnailUrl",
        "plainThumbnailUrl",
        "thumbnail",
        "proxyUrl",
        "proxyThumbUrl",
        "cipherUrl",
        "cipherThumbUrl",
        "originalUrl",
        "videoUrl",
        "downloadUrl",
    )

    private val primaryPlainUrlKeys = listOf("plainUrl")
    private val primaryUrlKeys = listOf("url", "fileUrl", "cipherUrl", "originalUrl")
    private val primaryProxyUrlKeys = listOf("proxyUrl")
    private val thumbnailPlainUrlKeys = listOf("plainThumbnailUrl")
    private val thumbnailUrlKeys = listOf("thumbnailUrl", "thumbnail", "cipherThumbUrl")
    private val thumbnailProxyUrlKeys = listOf("proxyThumbUrl")
    private val legacyProxyHosts = setOf("12da.rgzzsb.cn", "12da.yufengep.com")

    fun normalize(
        content: String,
        contentType: Int,
        objectMapper: Any,
        videoCompatPublicBase: String? = null,
        preserveClientEncrypted: Boolean = false,
    ): String {
        if (contentType !in mediaContentTypes || content.isBlank()) return content

        val trimmed = content.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return rewriteUrl(content, plainExtension = defaultExtensionFor(contentType), contentType = contentType)
        }

        val raw = runCatching { readJson(objectMapper, content, Any::class.java) }.getOrNull()
            ?: return content

        val normalized = when (raw) {
            is Map<*, *> -> normalizePayload(raw, contentType, preserveClientEncrypted, videoCompatPublicBase)
            is List<*> -> raw.map { normalizeNested(it, contentType, null) }
            else -> raw
        }
        return if (normalized == raw) content else writeJson(objectMapper, normalized)
    }

    fun normalizeNullable(
        content: String?,
        contentType: Int,
        objectMapper: Any,
        videoCompatPublicBase: String? = null,
    ): String? {
        return content?.let { normalize(it, contentType, objectMapper, videoCompatPublicBase) }
    }

    private fun normalizePayload(
        payload: Map<*, *>,
        contentType: Int,
        preserveClientEncrypted: Boolean,
        videoCompatPublicBase: String?,
    ): Map<String, Any?> {
        val out = payload.entries.associate { it.key.toString() to it.value }.toMutableMap()
        val encrypted = (out["encrypted"] as? Map<*, *>)?.entries
            ?.associate { it.key.toString() to it.value }
        val fallbackEncrypted = booleanValue(out["encryptedFallback"])
        val preserveEncryptedEnvelope = encrypted != null && (preserveClientEncrypted || fallbackEncrypted)
        val primaryExtension = inferPrimaryExtension(out, contentType)
        val thumbnailExtension = "jpg"

        for (key in directUrlKeys) {
            val value = out[key] as? String ?: continue
            val extension = if (isThumbnailKey(key)) thumbnailExtension else primaryExtension
            out[key] = if (preserveEncryptedEnvelope) {
                rewriteClientEncryptedUrl(value)
            } else {
                rewriteUrl(value, extension, contentType)
            }
        }

        if (encrypted != null) {
            val normalizedEncrypted = encrypted.toMutableMap()
            for (key in directUrlKeys) {
                val value = normalizedEncrypted[key] as? String ?: continue
                val extension = if (isThumbnailKey(key)) thumbnailExtension else primaryExtension
                normalizedEncrypted[key] = if (preserveEncryptedEnvelope) {
                    rewriteClientEncryptedUrl(value)
                } else {
                    rewriteUrl(value, extension, contentType)
                }
            }
            out["encrypted"] = normalizedEncrypted
        }

        firstUrl(out, primaryPlainUrlKeys)?.let {
            out["url"] = rewritePayloadUrl(it, primaryExtension, contentType, preserveEncryptedEnvelope)
        }
        if (stringValue(out["url"]).isNullOrBlank()) {
            firstUrl(out, primaryUrlKeys)?.let {
                out["url"] = rewritePayloadUrl(it, primaryExtension, contentType, preserveEncryptedEnvelope)
            }
                ?: firstUrl(encrypted, primaryUrlKeys)?.let {
                    out["url"] = rewritePayloadUrl(it, primaryExtension, contentType, preserveEncryptedEnvelope)
                }
                ?: firstUrl(out, primaryProxyUrlKeys)?.let {
                    out["url"] = rewritePayloadUrl(it, primaryExtension, contentType, preserveEncryptedEnvelope)
                }
                ?: firstUrl(encrypted, primaryProxyUrlKeys)?.let {
                    out["url"] = rewritePayloadUrl(it, primaryExtension, contentType, preserveEncryptedEnvelope)
                }
        }
        firstUrl(out, thumbnailPlainUrlKeys)?.let {
            out["thumbnailUrl"] = rewritePayloadUrl(it, thumbnailExtension, contentType, preserveEncryptedEnvelope)
        }
        if (stringValue(out["thumbnailUrl"]).isNullOrBlank()) {
            firstUrl(out, thumbnailUrlKeys)?.let {
                out["thumbnailUrl"] = rewritePayloadUrl(it, thumbnailExtension, contentType, preserveEncryptedEnvelope)
            }
                ?: firstUrl(encrypted, thumbnailUrlKeys)?.let {
                    out["thumbnailUrl"] = rewritePayloadUrl(it, thumbnailExtension, contentType, preserveEncryptedEnvelope)
                }
                ?: firstUrl(out, thumbnailProxyUrlKeys)?.let {
                    out["thumbnailUrl"] = rewritePayloadUrl(it, thumbnailExtension, contentType, preserveEncryptedEnvelope)
                }
                ?: firstUrl(encrypted, thumbnailProxyUrlKeys)?.let {
                    out["thumbnailUrl"] = rewritePayloadUrl(it, thumbnailExtension, contentType, preserveEncryptedEnvelope)
                }
        }

        applyVideoCompatibility(out, contentType, primaryExtension, videoCompatPublicBase)

        if (!preserveEncryptedEnvelope && !isEncryptedObjectUrl(stringValue(out["url"]))) {
            out.remove("encrypted")
            out.remove("proxyUrl")
            out.remove("proxyThumbUrl")
            out.remove("cipherUrl")
            out.remove("cipherThumbUrl")
            out.remove("cipherOssKey")
            out.remove("cipherThumbOssKey")
            out.remove("keyId")
            out.remove("alg")
        }

        return out.mapValues { (key, value) ->
            if (preserveEncryptedEnvelope && key == "encrypted") {
                value
            } else {
                normalizeNested(value, contentType, primaryExtension, preserveEncryptedEnvelope)
            }
        }
    }

    private fun normalizeNested(
        value: Any?,
        contentType: Int,
        plainExtension: String?,
        preserveClientEncrypted: Boolean = false,
    ): Any? {
        return when (value) {
            is Map<*, *> -> value.entries.associate { (key, item) ->
                val keyString = key.toString()
                val extension = when {
                    isThumbnailKey(keyString) -> "jpg"
                    keyString in directUrlKeys -> plainExtension ?: defaultExtensionFor(contentType)
                    else -> plainExtension
                }
                keyString to normalizeNested(item, contentType, extension, preserveClientEncrypted)
            }
            is List<*> -> value.map { normalizeNested(it, contentType, plainExtension, preserveClientEncrypted) }
            is String -> if (looksLikeUrl(value)) {
                if (preserveClientEncrypted) rewriteClientEncryptedUrl(value) else rewriteUrl(value, plainExtension, contentType)
            } else {
                value
            }
            else -> value
        }
    }

    private fun rewriteUrl(url: String, plainExtension: String?, contentType: Int): String {
        rewriteLegacyProxyUrl(url, plainExtension, contentType)?.let { return it }
        return LegacyStickerUrlRewriter.rewrite(url, plainExtension)
    }

    private fun applyVideoCompatibility(
        payload: MutableMap<String, Any?>,
        contentType: Int,
        plainExtension: String?,
        videoCompatPublicBase: String?,
    ) {
        val shouldBackfillPreview = !videoCompatPublicBase.isNullOrBlank()
        if (contentType != ContentType.VIDEO.value) return

        val currentUrl = stringValue(payload["url"])?.takeIf { it.isNotBlank() } ?: return
        val canonicalVideoUrl = if (isVideoCompatUrl(currentUrl)) {
            canonicalVideoUrlFromCompat(currentUrl)
        } else {
            canonicalVideoUrlFor(currentUrl, plainExtension)
        } ?: return
        val thumbnailUrl = videoThumbnailUrlFor(canonicalVideoUrl)
        if (stringValue(payload["thumbnailUrl"]).isNullOrBlank() && thumbnailUrl != null) {
            payload["thumbnailUrl"] = thumbnailUrl
        }
        if (thumbnailUrl != null) {
            payload["thumbnail"] = thumbnailUrl
        }
        payload.putIfAbsent("videoUrl", canonicalVideoUrl)
        payload.putIfAbsent("originalUrl", canonicalVideoUrl)

        payload["url"] = canonicalVideoUrl
        if (thumbnailUrl != null && shouldBackfillPreview) {
            payload.putIfAbsent("previewUrl", thumbnailUrl)
        }
    }

    private fun canonicalVideoUrlFor(url: String, plainExtension: String?): String? {
        val rewritten = rewriteUrl(url, plainExtension ?: "mp4", ContentType.VIDEO.value)
        val uri = runCatching { URI(rewritten) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != URI(MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT).host.lowercase()) return null

        val parts = uri.rawPath.orEmpty().trimStart('/').split('/').filter { it.isNotBlank() }
        if (parts.size != 2 || parts[0] != "videos") return null
        val fileName = parts[1]
        val ext = normalizeExtension(fileName.substringAfterLast('.', ""))
        if (ext !in setOf("mp4", "mov", "m4v")) return null
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "${MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT}/videos/$fileName$query$fragment"
    }

    private fun canonicalVideoUrlFromCompat(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val fileName = uri.rawPath.orEmpty()
            .substringAfter("/api/media/compat/videos/", "")
            .takeIf { it.isNotBlank() }
            ?: return null
        val ext = normalizeExtension(fileName.substringAfterLast('.', ""))
        if (ext !in setOf("mp4", "mov", "m4v")) return null
        return "${MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT}/videos/$fileName"
    }

    private fun videoThumbnailUrlFor(videoUrl: String): String? {
        val uri = runCatching { URI(videoUrl) }.getOrNull() ?: return null
        val fileName = uri.rawPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return null
        val mediaId = fileName.substringBeforeLast('.', "")
        if (mediaId.isBlank()) return null
        return "${MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT}/thumbnails/videos/$mediaId.jpg"
    }

    private fun isVideoCompatUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.rawPath.orEmpty().contains("/api/media/compat/videos/")
    }

    private fun rewritePayloadUrl(
        url: String,
        plainExtension: String?,
        contentType: Int,
        preserveClientEncrypted: Boolean,
    ): String {
        return if (preserveClientEncrypted) rewriteClientEncryptedUrl(url) else rewriteUrl(url, plainExtension, contentType)
    }

    private fun rewriteClientEncryptedUrl(url: String): String {
        rewriteLegacyProxyHost(url)?.let { return it }
        return MediaEndpointPolicy.rewriteDeprecatedCipherUrl(url)
    }

    private fun rewriteLegacyProxyHost(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "12da.rgzzsb.cn") return null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "${uri.scheme ?: "https"}://12da.yufengep.com$port${uri.rawPath.orEmpty()}$query$fragment"
    }

    private fun rewriteLegacyProxyUrl(url: String, plainExtension: String?, contentType: Int): String? {
        val category = categoryFor(contentType) ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in legacyProxyHosts) return null

        val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotBlank() }
        val apiIndex = parts.windowed(2).indexOfFirst { it[0] == "api" && it[1] == "media" }
        if (apiIndex < 0 || apiIndex + 3 >= parts.size) return null
        if (parts.getOrNull(apiIndex + 2) == "compat") return null

        val mediaId = parts[apiIndex + 2].takeIf { it.isNotBlank() } ?: return null
        val tokenAndExt = parts[apiIndex + 3]
        val rawToken = tokenAndExt.substringBeforeLast('.', "")
        val isThumbnail = rawToken.endsWith("_t")
        val ext = if (isThumbnail) {
            "jpg"
        } else {
            normalizeExtension(tokenAndExt.substringAfterLast('.', "")) ?: plainExtension
        } ?: return null

        val objectKey = if (isThumbnail) {
            "thumbnails/$category/$mediaId.jpg"
        } else {
            "$category/$mediaId.$ext"
        }
        return "${MediaEndpointPolicy.CANONICAL_PUBLIC_ENDPOINT}/$objectKey"
    }

    private fun firstUrl(payload: Map<String, Any?>?, keys: List<String>): String? {
        if (payload == null) return null
        return keys.firstNotNullOfOrNull { key ->
            (payload[key] as? String)?.takeIf { it.isNotBlank() }
        }
    }

    private fun stringValue(value: Any?): String? = value as? String

    private fun booleanValue(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun inferPrimaryExtension(payload: Map<String, Any?>, contentType: Int): String? {
        explicitExtension(payload, "ext")?.let { return it }
        explicitExtension(payload, "extension")?.let { return it }
        explicitExtension(payload, "fileExtension")?.let { return it }
        extensionFromName(payload["name"] as? String)?.let { return it }
        primaryPlainUrlKeys.forEach { key ->
            extensionFromUrl(payload[key] as? String)?.let { return it }
        }
        primaryUrlKeys.forEach { key ->
            extensionFromUrl(payload[key] as? String)?.let { return it }
        }
        primaryProxyUrlKeys.forEach { key ->
            extensionFromUrl(payload[key] as? String)?.let { return it }
        }
        return defaultExtensionFor(contentType)
    }

    private fun explicitExtension(payload: Map<String, Any?>, key: String): String? {
        val value = payload[key] as? String ?: return null
        return normalizeExtension(value)
    }

    private fun extensionFromName(name: String?): String? {
        val fileName = name?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
        return normalizeExtension(fileName.substringAfterLast('.', ""))
    }

    private fun extensionFromUrl(url: String?): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val fileName = uri.rawPath?.substringAfterLast('/') ?: return null
        return normalizeExtension(fileName.substringAfterLast('.', ""))
    }

    private fun defaultExtensionFor(contentType: Int): String? {
        return when (contentType) {
            ContentType.VOICE.value -> "m4a"
            ContentType.VIDEO.value -> "mp4"
            else -> null
        }
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

    private fun normalizeExtension(extension: String?): String? {
        val value = extension
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "bin" && "/" !in it }
        return if (value in setOf("jpg", "jpeg")) "jpg" else value
    }

    private fun isThumbnailKey(key: String): Boolean {
        return key.contains("thumb", ignoreCase = true) || key == "thumbnail"
    }

    private fun looksLikeUrl(value: String): Boolean {
        return value.startsWith("http://") || value.startsWith("https://")
    }

    private fun isEncryptedObjectUrl(url: String?): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.path.split('/').filter { it.isNotBlank() }.firstOrNull() == "encrypted"
    }

    private fun readJson(objectMapper: Any, content: String, type: Class<*>): Any? {
        val method = objectMapper.javaClass.methods.firstOrNull { method ->
            method.name == "readValue" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Class::class.java
        } ?: error("${objectMapper.javaClass.name} does not expose readValue(String, Class)")
        return method.invoke(objectMapper, content, type)
    }

    private fun writeJson(objectMapper: Any, value: Any?): String {
        val method = objectMapper.javaClass.methods.firstOrNull { method ->
            method.name == "writeValueAsString" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Any::class.java
        } ?: error("${objectMapper.javaClass.name} does not expose writeValueAsString(Object)")
        return method.invoke(objectMapper, value) as String
    }
}
