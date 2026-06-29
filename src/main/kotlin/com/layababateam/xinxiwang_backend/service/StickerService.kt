package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.model.Sticker
import com.layababateam.xinxiwang_backend.repository.StickerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class StickerService(
    private val stickerRepository: StickerRepository,
    private val ossService: OssService,
    private val endpointResolver: MediaEndpointResolver,
) {
    private val log = LoggerFactory.getLogger(StickerService::class.java)

    companion object {
        private const val MAX_STICKER_BYTES: Long = 10L * 1024 * 1024
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    /**
     * Upload a new sticker image to OSS and save it to the user's favorites.
     *
     * Stickers are stored under the `stickers/` prefix in cleartext — they are
     * not message content and have no per-user secret, so encrypting them
     * would only break OSS-side CDN caching without adding privacy.
     */
    fun uploadAndSaveSticker(userId: String, file: MultipartFile): Sticker {
        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast('.', "").lowercase()

        if (extension.isEmpty() || extension !in ALLOWED_EXTENSIONS) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "仅支持 ${ALLOWED_EXTENSIONS.joinToString(", ")} 格式")
        }
        if (file.size > MAX_STICKER_BYTES) {
            throw BusinessException(
                ErrorCode.INVALID_PARAM,
                "文件大小 ${file.size / (1024 * 1024)}MB 超过 stickers 类别限制 ${MAX_STICKER_BYTES / (1024 * 1024)}MB",
            )
        }

        val mime = file.contentType ?: "application/octet-stream"
        val objectKey = "stickers/${UUID.randomUUID()}.$extension"
        val bytes = file.bytes

        ossService.putObject(objectKey, bytes, mime)
        val url = "${endpointResolver.currentOssPublicEndpoint()}/$objectKey"
        log.info("Uploaded sticker for user={} key={}", userId, objectKey)
        return saveFavoriteSticker(userId, url)
    }

    /**
     * Save an existing sticker (from its URL) to the user's favorites.
     * Prevents duplicate favorites.
     */
    fun saveFavoriteSticker(userId: String, url: String): Sticker {
        val existing = stickerRepository.findByUserIdAndOriginalUrl(userId, url)
        if (existing != null) {
            return existing
        }

        val sticker = Sticker(
            userId = userId,
            originalUrl = url,
            isFavorite = true,
            sortOrder = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        return stickerRepository.save(sticker)
    }

    /**
     * Get all favorite stickers for a user.
     */
    fun getFavoriteStickers(userId: String): List<Sticker> {
        return stickerRepository.findByUserIdOrderBySortOrderDescCreatedAtDesc(userId)
    }

    /**
     * Remove a sticker from the user's favorites.
     */
    fun removeFavoriteSticker(userId: String, stickerId: String): Boolean {
        val sticker = stickerRepository.findById(stickerId).orElse(null)
        if (sticker != null && sticker.userId == userId) {
            stickerRepository.delete(sticker)
            return true
        }
        return false
    }
}
