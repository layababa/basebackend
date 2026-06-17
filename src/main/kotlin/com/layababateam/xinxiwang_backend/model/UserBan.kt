package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_bans")
@CompoundIndexes(
    CompoundIndex(def = "{'userId': 1, 'isActive': 1}"),
    CompoundIndex(def = "{'isActive': 1, 'type': 1, 'expiresAt': 1}")
)
data class UserBan(
    @Id
    val id: String? = null,
    val userId: String,
    val adminId: String,
    val reason: String,
    val type: String,
    val expiresAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val unbannedAt: Long? = null,
    val unbannedBy: String? = null
)
