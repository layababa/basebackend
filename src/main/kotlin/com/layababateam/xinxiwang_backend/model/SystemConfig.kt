package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "system_config")
data class SystemConfig(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val key: String,
    val value: String,
    val description: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
