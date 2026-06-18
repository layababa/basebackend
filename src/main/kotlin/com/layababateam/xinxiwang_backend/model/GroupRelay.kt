package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 群接龙（一个群里发起的接龙活动）。
 * 接龙卡片本身以 contentType=19 的聊天消息形式出现在会话中（messageId 关联），
 * 接龙条目持久化在本集合，参与/关闭通过 WS 实时广播 group_relay_updated。
 */
@Document(collection = "group_relays")
data class GroupRelay(
    @Id val id: String? = null,
    @Indexed val conversationId: String,
    val creatorId: String,
    val creatorName: String,
    val title: String,
    val description: String? = null,
    /** 0=进行中, 1=已结束 */
    val status: Int = 0,
    /** 关联的接龙卡片消息 id */
    val messageId: String? = null,
    val entries: List<RelayEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
)

data class RelayEntry(
    /** 接龙序号，从 1 开始 */
    val seq: Int,
    val userId: String,
    val userName: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)
