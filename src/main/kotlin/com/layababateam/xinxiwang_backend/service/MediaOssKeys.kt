package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ContentType
import com.layababateam.xinxiwang_backend.model.MediaObject
import java.net.URI

data class EncryptedMediaOssKey(
    val category: String,
    val mediaId: String,
    val thumb: Boolean,
)

fun resolveDirectUploadDir(category: String): String =
    if (category in DIRECT_UPLOAD_DIRS) category else "files"

fun normalizeDirectUploadCategory(category: String?): String =
    when (StringValueRules.lowerNonBlank(category)) {
        "image", "images", "photo", "photos" -> "images"
        "video", "videos" -> "videos"
        "voice", "voices", "audio" -> "audio"
        "file", "files" -> "files"
        "avatar", "avatars" -> "avatars"
        "sticker", "stickers" -> "stickers"
        "moment_image", "moment_images" -> "moment_images"
        "moment_video", "moment_videos" -> "moment_videos"
        "feedback_image", "feedback_images" -> "feedback_images"
        else -> "files"
    }

fun directUploadSizeLimitBytes(category: String): Long =
    DIRECT_UPLOAD_SIZE_LIMIT_BYTES[category] ?: DIRECT_UPLOAD_SIZE_LIMIT_BYTES.getValue("files")

fun parseEncryptedMainCategory(ossKey: String, mediaId: String): String? {
    val parsed = parseEncryptedMediaOssKey(ossKey) ?: return null
    if (parsed.thumb || parsed.mediaId != mediaId || "/" in parsed.category) return null
    return parsed.category.takeIf { it.isNotBlank() }
}

fun expectedEncryptedThumbnailOssKey(category: String, mediaId: String): String =
    "encrypted/thumbnails/$category/$mediaId.bin"

fun isExpectedEncryptedThumbnailOssKey(thumbOssKey: String, category: String, mediaId: String): Boolean =
    thumbOssKey == expectedEncryptedThumbnailOssKey(category, mediaId)

fun convertedPlainMediaOssKey(category: String, mediaId: String, ext: String): String? {
    val normalizedCategory = category.trim().trim('/')
    val normalizedExt = normalizedPlainMediaExt(normalizedCategory, ext)
    if (normalizedCategory.isBlank() || mediaId.isBlank() || normalizedExt.isBlank()) return null
    return "$normalizedCategory/$mediaId.$normalizedExt"
}

fun convertedPlainMediaOssKeyCandidates(category: String, mediaId: String, ext: String): List<String> {
    val normalizedCategory = category.trim().trim('/')
    val primaryExt = normalizedPlainMediaExt(normalizedCategory, ext)
    val sniffedExts = when (normalizedCategory) {
        "images", "moment_images", "feedback_images", "avatars", "stickers" ->
            listOf("jpg", "jpeg", "png", "webp", "gif", "bin")
        "videos", "moment_videos" ->
            listOf("mp4", "mov", "bin")
        "audio" ->
            listOf("m4a", "mp3", "aac", "wav", "ogg", "amr", "flac", "bin")
        else ->
            listOf("bin")
    }

    return (listOf(primaryExt) + sniffedExts)
        .let(StringListRules::nonBlank)
        .mapNotNull { convertedPlainMediaOssKey(normalizedCategory, mediaId, it) }
}

fun convertedPlainThumbnailOssKey(category: String, mediaId: String): String? {
    val normalizedCategory = category.trim().trim('/')
    if (normalizedCategory.isBlank() || mediaId.isBlank()) return null
    return "thumbnails/$normalizedCategory/$mediaId.jpg"
}

fun mediaObjectPathFromUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val path = runCatching { URI(value).rawPath }.getOrNull()
        ?: value.substringBefore('?').substringBefore('#')
    return path.trimStart('/').takeIf { it.isNotBlank() }
}

fun mediaExtensionFromUrl(value: String?): String? {
    val path = mediaObjectPathFromUrl(value) ?: return null
    return normalizedFileExtension(cleanFileNameFromPath(path))
}

fun encryptedMediaOssKeyFromUrl(value: String): String? =
    mediaObjectPathFromUrl(value)

fun cleanFileNameFromPath(value: String?): String? =
    value?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

fun fileStem(fileName: String?): String? {
    val cleanFileName = cleanFileNameFromPath(fileName) ?: return null
    return cleanFileName.substringBeforeLast('.', cleanFileName).takeIf { it.isNotBlank() }
}

fun normalizedFileExtension(value: String?): String? {
    val raw = StringValueRules.nonBlank(value) ?: return null
    val extension = if ('.' in raw) raw.substringAfterLast('.') else raw
    return extension
        .trimStart('.')
        .lowercase()
        .takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) }
}

fun defaultPlainMediaExtension(contentType: Int): String? = when (contentType) {
    ContentType.IMAGE.value -> "jpg"
    ContentType.VIDEO.value -> "mp4"
    ContentType.VOICE.value -> "m4a"
    else -> null
}

fun defaultPlainMediaCategory(contentType: Int): String? = when (contentType) {
    ContentType.IMAGE.value -> "images"
    ContentType.VIDEO.value -> "videos"
    ContentType.VOICE.value -> "audio"
    ContentType.FILE.value -> "files"
    else -> null
}

fun parseEncryptedMediaOssKey(ossKey: String?): EncryptedMediaOssKey? {
    val restWithPrefix = ossKey?.trim()?.trimStart('/') ?: return null
    val rest = restWithPrefix.removePrefix("encrypted/")
    if (rest == restWithPrefix || !rest.endsWith(".bin")) return null

    val withoutSuffix = rest.removeSuffix(".bin")
    val thumb = withoutSuffix.startsWith("thumbnails/")
    val categoryAndId = if (thumb) {
        withoutSuffix.removePrefix("thumbnails/")
    } else {
        withoutSuffix
    }
    val slashIdx = categoryAndId.lastIndexOf('/')
    if (slashIdx <= 0 || slashIdx == categoryAndId.length - 1) return null

    val category = categoryAndId.substring(0, slashIdx)
    val mediaId = categoryAndId.substring(slashIdx + 1)
    if (category.isBlank() || mediaId.isBlank()) return null
    return EncryptedMediaOssKey(category = category, mediaId = mediaId, thumb = thumb)
}

fun resolvePlainMediaOssKey(media: MediaObject, thumb: Boolean): String? {
    if (thumb) {
        media.plainThumbOssKey.nonBlank()?.let { return it }
        if (media.thumbOssKey.nonBlank() == null) return null
        return convertedPlainThumbnailOssKey(media.category, media.mediaId)
    }

    media.plainOssKey.nonBlank()?.let { return it }
    media.ossKey.nonBlank()?.takeUnless { it.startsWith("encrypted/") }?.let { return it }
    return convertedPlainMediaOssKey(media.category, media.mediaId, media.ext)
}

private fun String?.nonBlank(): String? =
    this?.takeIf { it.isNotBlank() }

private val DIRECT_UPLOAD_DIRS = setOf(
    "avatars",
    "images",
    "videos",
    "audio",
    "files",
    "stickers",
    "moment_images",
    "moment_videos",
    "feedback_images",
)

private val DIRECT_UPLOAD_SIZE_LIMIT_BYTES = mapOf(
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

private fun normalizedPlainMediaExt(category: String, ext: String): String {
    val requestedExt = ext.trim().trimStart('.').lowercase()
    return when {
        category == "audio" && requestedExt == "mp4" -> "m4a"
        else -> requestedExt
    }
}
