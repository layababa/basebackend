package com.layababateam.xinxiwang_backend.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "bots")
data class Bot(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val userId: String,
    @Indexed(unique = true)
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val description: String = "",
    @JsonIgnore
    val apiKeyHash: String,
    val createdBy: String,
    val status: Int = 1,              // 0=禁用, 1=启用
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
