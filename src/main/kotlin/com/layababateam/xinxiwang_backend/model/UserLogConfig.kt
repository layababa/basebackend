package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_log_configs")
data class UserLogConfig(
    @Id val id: String? = null,
    @Indexed(unique = true) val userId: String,
    val criticalLogEnabled: Boolean = true,
    val revision: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val updatedBy: String? = null,
    val ackedDeviceIds: Set<String> = emptySet(),
)
