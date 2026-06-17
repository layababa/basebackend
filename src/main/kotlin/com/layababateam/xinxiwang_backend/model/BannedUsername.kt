package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "banned_usernames")
data class BannedUsername(
    @Id val id: String? = null,
    @Indexed(unique = true)
    val username: String,
    val bannedAt: Long = System.currentTimeMillis()
)
