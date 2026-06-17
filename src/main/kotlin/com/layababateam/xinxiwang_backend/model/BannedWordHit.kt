package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "banned_word_hits")
data class BannedWordHit(
    @Id
    val id: String? = null,
    @Indexed
    val senderId: String,
    val senderName: String,
    val conversationId: String,
    val conversationType: Int,      // 0=私聊, 1=群聊
    val targetName: String?,        // 群名或对方用户名
    val originalContent: String,    // 原始消息内容
    val matchedWord: String,        // 命中的敏感词
    val action: String = "BLOCKED", // BLOCKED/WARNED
    val createdAt: Long = System.currentTimeMillis()
)
