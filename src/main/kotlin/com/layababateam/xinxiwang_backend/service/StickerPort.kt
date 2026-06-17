package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Sticker
import org.springframework.web.multipart.MultipartFile

interface StickerPort {
    fun uploadAndSaveSticker(userId: String, file: MultipartFile): Sticker

    fun saveFavoriteSticker(userId: String, url: String): Sticker

    fun getFavoriteStickers(userId: String): List<Sticker>

    fun removeFavoriteSticker(userId: String, stickerId: String): Boolean
}
