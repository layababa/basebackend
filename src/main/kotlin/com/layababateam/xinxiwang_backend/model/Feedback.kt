package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "feedbacks")
data class Feedback(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,                 // 提交者 userId
    val content: String,               // 反馈内容
    @Indexed
    val status: String = "PENDING",    // "PENDING" | "RESOLVED" | "REJECTED"
    val adminNote: String = "",        // 管理员回复
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val assignedTo: String? = null,   // 指派的管理员 ID
    val replies: List<FeedbackReply> = emptyList(),
    val images: List<String> = emptyList()  // 反馈截图 URL 列表
)

data class FeedbackReply(
    val adminId: String,
    val adminUsername: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
