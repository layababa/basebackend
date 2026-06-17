package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "messages")
data class Message(
    @Id
    val id: String? = null,
    val conversationId: String,    // 所属会话
    val senderId: String,          // 发送者 UID
    val contentType: Int = 0,      // 0=text, 1=image, 2=voice, 3=video
    val content: String,           // 消息内容
    val seqId: Long,               // 单调递增序列号 (per conversation)
    val isRecalled: Boolean = false,
    val deletedBy: List<String> = emptyList(),
    val mentions: List<String> = emptyList(),  // @提及的用户 UID 列表
    val replyToMessageId: String? = null,      // 引用回复的原消息 ID
    val createdAt: Long = System.currentTimeMillis()
)
