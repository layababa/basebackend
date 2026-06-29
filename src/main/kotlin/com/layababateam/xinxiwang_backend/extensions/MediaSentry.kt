package com.layababateam.xinxiwang_backend.extensions

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

object MediaSentry {
    fun breadcrumb(
        stage: String,
        mediaType: String,
        message: String,
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any?> = emptyMap(),
    ) {
        val crumb = Breadcrumb().apply {
            category = "media.$stage"
            this.message = message
            this.level = level
            setData("media.type", mediaType)
            setData("media.stage", stage)
            sanitizedData(data).forEach { (key, value) -> setData(key, value) }
        }
        Sentry.addBreadcrumb(crumb)
    }

    fun captureSampled(
        stage: String,
        mediaType: String,
        message: String,
        dedupKeyParts: List<String>,
        level: SentryLevel = SentryLevel.WARNING,
        data: Map<String, Any?> = emptyMap(),
    ) {
        val dedupKey = buildString {
            append("media:")
            append(stage)
            append(':')
            append(mediaType)
            dedupKeyParts.forEach {
                append(':')
                append(it.take(96))
            }
        }
        SentryReporter.captureSampled(
            dedupKey = dedupKey,
            message = "[MEDIA] $message",
            level = level,
            tags = mapOf(
                "category" to "media.$stage",
                "media.type" to mediaType,
                "media.stage" to stage,
            ),
            extras = sanitizedData(data),
        )
    }

    fun mediaTypeFromCategory(category: String?): String {
        val normalized = category?.trim()?.lowercase().orEmpty()
        return when {
            "video" in normalized -> "video"
            "image" in normalized || "photo" in normalized ||
                "avatar" in normalized || "sticker" in normalized -> "image"
            "audio" in normalized || "voice" in normalized -> "audio"
            else -> "file"
        }
    }

    private fun sanitizedData(data: Map<String, Any?>): Map<String, Any?> {
        return data
            .filterKeys { key ->
                val lower = key.lowercase()
                "token" !in lower && "signature" !in lower && "authorization" !in lower
            }
            .mapValues { (_, value) -> sanitizeValue(value) }
    }

    private fun sanitizeValue(value: Any?): Any? {
        return when (value) {
            is String -> sanitizeText(value)
            is Iterable<*> -> value.map { sanitizeValue(it) }
            is Map<*, *> -> value.entries.associate { entry ->
                entry.key.toString() to sanitizeValue(entry.value)
            }
            else -> value
        }
    }

    private fun sanitizeText(value: String): String {
        return URL_REGEX.replace(value) { match ->
            val uri = runCatching { java.net.URI(match.value) }.getOrNull()
            if (uri == null) {
                "<url>"
            } else {
                val port = if (uri.port > 0) ":${uri.port}" else ""
                val path = sanitizePath(uri.path ?: "")
                "${uri.scheme}://${uri.host}$port$path"
            }
        }
    }

    private fun sanitizePath(path: String): String {
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.size >= 4 && segments[0] == "api" && segments[1] == "media") {
            val ext = segments.last().substringAfterLast('.', "")
            val suffix = if (ext.isBlank()) "" else ".$ext"
            return "/api/media/${segments[2]}/<token>$suffix"
        }
        return path
    }

    private val URL_REGEX = Regex("""https?://[^\s)]+""", RegexOption.IGNORE_CASE)
}
