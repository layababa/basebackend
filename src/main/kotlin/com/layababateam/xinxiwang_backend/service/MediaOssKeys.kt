package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.MediaObject

data class EncryptedMediaOssKey(
    val category: String,
    val mediaId: String,
    val thumb: Boolean,
)

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
        .filter { it.isNotBlank() }
        .distinct()
        .mapNotNull { convertedPlainMediaOssKey(normalizedCategory, mediaId, it) }
}

fun convertedPlainThumbnailOssKey(category: String, mediaId: String): String? {
    val normalizedCategory = category.trim().trim('/')
    if (normalizedCategory.isBlank() || mediaId.isBlank()) return null
    return "thumbnails/$normalizedCategory/$mediaId.jpg"
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

private fun normalizedPlainMediaExt(category: String, ext: String): String {
    val requestedExt = ext.trim().trimStart('.').lowercase()
    return when {
        category == "audio" && requestedExt == "mp4" -> "m4a"
        else -> requestedExt
    }
}
