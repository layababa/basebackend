package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "user_conversations")
data class UserConversation(
    @Id
    val id: String? = null,
    val userId: String,
    val conversationId: String,
    val lastReadTime: Long = 0,
    val readSeqId: Long = 0,
    val muted: Boolean = false,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // ── 群组扩展 ──
    val myNickname: String? = null,
    val groupRemark: String? = null,
    val savedToContacts: Boolean = false,
    val mentionedSeqIds: List<Long> = emptyList(),
    val lastActiveAt: Long = 0,
    val peerRemark: String? = null,

    // ── 非好友状态删除水位线：本用户不可见 seqId <= hiddenBeforeSeqId 的消息 ──
    val hiddenBeforeSeqId: Long = 0,
    // ── 会话级软删除：true 时该会话从列表里隐藏（仍保留水位线，避免重新拉回历史）──
    val deleted: Boolean = false
)
