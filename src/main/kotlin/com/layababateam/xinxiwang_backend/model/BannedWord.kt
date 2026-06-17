package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "banned_words")
data class BannedWord(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val word: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)
