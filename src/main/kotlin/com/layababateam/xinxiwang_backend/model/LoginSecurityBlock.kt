package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "login_security_blocks")
data class LoginSecurityBlock(
    @Id
    val id: String? = null,
    @Indexed
    val type: String,
    @Indexed
    val value: String,
    val reason: String,
    val source: String = "AUTO",
    val createdBy: String? = null,
    @Indexed
    val active: Boolean = true,
    val expiresAt: Long? = null,
    val revokedAt: Long? = null,
    val revokedBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
