package com.layababateam.xinxiwang_backend.service

import java.net.URI

object MediaEndpointPolicy {
    const val CANONICAL_PUBLIC_ENDPOINT = "https://rentmsg-hk.oss-accelerate.aliyuncs.com"

    private val encryptedPlainExtByCategory = mapOf(
        "audio" to "m4a",
        "videos" to "mp4",
    )

    private val retiredMediaHosts = setOf(
        "oss.rgzzsb.cn",
        "s3.12da.rgzzsb.cn",
        "s3.12da.yufengep.com",
        "rentmsg-media.s3-accelerate.amazonaws.com",
        "rentmsg.s3-accelerate.amazonaws.com",
    )

    private val canonicalHost = URI(CANONICAL_PUBLIC_ENDPOINT).host.lowercase()

    fun canonicalizePublicEndpoint(rawEndpoint: String): String {
        val trimmed = rawEndpoint.trim().trimEnd('/')
        if (trimmed.isBlank()) return CANONICAL_PUBLIC_ENDPOINT

        val uri = runCatching { URI(trimmed) }.getOrNull()
        val host = uri?.host?.lowercase()
        return if (host in retiredMediaHosts) CANONICAL_PUBLIC_ENDPOINT else trimmed
    }

    fun rewriteDeprecatedMediaUrl(url: String, plainExtension: String? = null): String {
        return rewriteDeprecatedMediaUrl(url, rewriteEncryptedPath = true, plainExtension = plainExtension)
    }

    fun rewriteDeprecatedCipherUrl(url: String): String {
        return rewriteDeprecatedMediaUrl(url, rewriteEncryptedPath = false, plainExtension = null)
    }

    private fun rewriteDeprecatedMediaUrl(
        url: String,
        rewriteEncryptedPath: Boolean,
        plainExtension: String?,
    ): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host?.lowercase() ?: return url
        if (host !in retiredMediaHosts) return url

        val path = rewriteObjectPath(uri.rawPath.orEmpty(), rewriteEncryptedPath, plainExtension)
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "https://$canonicalHost$path$query$fragment"
    }

    private fun rewriteObjectPath(rawPath: String, rewriteEncryptedPath: Boolean, plainExtension: String?): String {
        if (!rewriteEncryptedPath) return rawPath

        val leadingSlash = rawPath.startsWith("/")
        val path = rawPath.trimStart('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.firstOrNull() != "encrypted") return rawPath

        val rewritten = when {
            parts.size >= 4 && parts[1] == "thumbnails" -> {
                val category = parts[2]
                val mediaId = parts[3].substringBeforeLast('.')
                if (mediaId.isBlank()) null else "thumbnails/$category/$mediaId.jpg"
            }
            parts.size >= 3 -> {
                val category = parts[1]
                val filename = parts[2]
                val mediaId = filename.substringBeforeLast('.')
                val sourceExt = normalizeExtension(filename.substringAfterLast('.', ""))
                val targetExt = if (!sourceExt.isNullOrBlank()) {
                    sourceExt
                } else {
                    normalizeExtension(plainExtension) ?: encryptedPlainExtByCategory[category]
                }
                if (mediaId.isBlank() || targetExt.isNullOrBlank()) null else "$category/$mediaId.$targetExt"
            }
            else -> null
        } ?: return rawPath

        return if (leadingSlash) "/$rewritten" else rewritten
    }

    private fun normalizeExtension(extension: String?): String? {
        val value = extension
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "bin" && "/" !in it }
        return if (value in setOf("jpg", "jpeg")) "jpg" else value
    }
}
