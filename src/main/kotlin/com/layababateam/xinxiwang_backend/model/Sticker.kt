package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "stickers")
data class Sticker(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,          // The ID of the user who saved/owns this sticker
    val originalUrl: String,     // Remote absolute URL
    val isFavorite: Boolean = true,
    val sortOrder: Long = System.currentTimeMillis(), // For sorting in the sticker panel
    val createdAt: Long = System.currentTimeMillis() // When it was created/favorited by the user
)
