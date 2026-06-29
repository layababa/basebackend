package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Sticker
import java.net.URI

object LegacyStickerUrlRewriter {
    private val hostMap = mapOf(
        "12da.rgzzsb.cn" to "12da.yufengep.com",
    )

    fun rewrite(url: String, plainExtension: String? = null): String {
        val mediaUrl = MediaEndpointPolicy.rewriteDeprecatedMediaUrl(url, plainExtension)
        if (mediaUrl != url) return mediaUrl

        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val replacementHost = hostMap[uri.host?.lowercase()] ?: return url

        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "https://$replacementHost$path$query$fragment"
    }

    fun rewrite(sticker: Sticker): Sticker {
        val rewrittenUrl = rewrite(sticker.originalUrl)
        return if (rewrittenUrl == sticker.originalUrl) sticker else sticker.copy(originalUrl = rewrittenUrl)
    }
}
