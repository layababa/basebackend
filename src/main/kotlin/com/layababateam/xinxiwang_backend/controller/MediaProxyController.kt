package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.service.MediaProxyPort
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/media")
class MediaProxyController(
    private val mediaProxyPort: MediaProxyPort,
) {
    private val log = LoggerFactory.getLogger(MediaProxyController::class.java)

    @GetMapping("/{mediaId}/{tokenAndExt:.+}")
    fun proxyMain(
        @PathVariable mediaId: String,
        @PathVariable tokenAndExt: String,
    ): ResponseEntity<Void> {
        val parsed = parseTokenAndVariant(tokenAndExt) ?: return ResponseEntity.badRequest().build()
        if (!mediaProxyPort.verifyMediaToken(mediaId, parsed.token)) {
            log.warn("Media redirect: bad token for mediaId={}", mediaId)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return redirectToOss(
            mediaId = mediaId,
            thumb = parsed.thumb,
            ossKey = mediaProxyPort.resolveMediaOssKey(mediaId, parsed.thumb),
        )
    }

    @GetMapping("/compat/videos/{fileName:.+}")
    fun compatVideo(
        @PathVariable fileName: String,
        @RequestParam("variant", required = false) variant: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val cleanFileName = fileName.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().build()
        val wantsThumbnail = compatRequestWantsThumbnail(variant, request)
        return redirectToOss(
            mediaId = cleanFileName.substringBeforeLast('.', cleanFileName),
            thumb = wantsThumbnail,
            ossKey = mediaProxyPort.resolveCompatVideoOssKey(cleanFileName, wantsThumbnail),
        )
    }

    private fun redirectToOss(mediaId: String, thumb: Boolean, ossKey: String?): ResponseEntity<Void> {
        if (ossKey.isNullOrBlank()) {
            log.warn("Media redirect: missing decrypted OSS key for mediaId={} thumb={}", mediaId, thumb)
            return ResponseEntity.notFound().build()
        }

        val endpoint = mediaProxyPort.currentOssPublicEndpoint().trimEnd('/')
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create("$endpoint/$ossKey"))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
            .build()
    }

    private fun compatRequestWantsThumbnail(variant: String?, request: HttpServletRequest): Boolean {
        val normalizedVariant = variant?.lowercase()
        if (normalizedVariant == "thumb" || normalizedVariant == "thumbnail") return true
        if (!request.getHeader(HttpHeaders.RANGE).isNullOrBlank()) return false

        val accept = request.getHeader(HttpHeaders.ACCEPT)?.lowercase().orEmpty()
        return "image/" in accept && "video/" !in accept
    }

    private fun parseTokenAndVariant(tokenAndExt: String): ParsedToken? {
        val dotIndex = tokenAndExt.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == tokenAndExt.length - 1) return null
        val rawToken = tokenAndExt.substring(0, dotIndex)
        val thumb = rawToken.endsWith(THUMB_TOKEN_SUFFIX)
        val token = if (thumb) rawToken.removeSuffix(THUMB_TOKEN_SUFFIX) else rawToken
        if (token.isBlank()) return null
        return ParsedToken(token = token, thumb = thumb)
    }

    private data class ParsedToken(val token: String, val thumb: Boolean)

    private companion object {
        const val THUMB_TOKEN_SUFFIX = "_t"
    }
}
