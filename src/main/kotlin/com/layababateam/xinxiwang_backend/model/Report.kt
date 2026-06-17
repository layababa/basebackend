package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "reports")
data class Report(
    @Id
    val id: String? = null,
    @Indexed
    val reporterId: String,           // 举报人 userId
    @Indexed
    val targetId: String,             // 被举报的 userId 或 messageId
    val targetType: String,           // "USER" | "MESSAGE"
    val category: String,             // 举报分类
    val description: String = "",     // 补充说明
    @Indexed
    val status: String = "PENDING",   // "PENDING" | "RESOLVED" | "REJECTED"
    val adminNote: String = "",       // 管理员备注
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val handledBy: String? = null,    // 处理该举报的管理员 ID
    val handledAt: Long? = null,
    val resolution: String? = null    // 处理措施说明
)
