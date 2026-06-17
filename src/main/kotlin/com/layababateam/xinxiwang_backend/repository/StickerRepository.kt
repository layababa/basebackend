package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Sticker
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StickerRepository : MongoRepository<Sticker, String> {
    fun findByUserIdOrderBySortOrderDescCreatedAtDesc(userId: String): List<Sticker>

    fun findByUserIdAndOriginalUrl(userId: String, originalUrl: String): Sticker?
}
