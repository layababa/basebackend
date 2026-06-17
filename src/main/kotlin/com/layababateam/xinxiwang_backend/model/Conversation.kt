package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "conversations")
@CompoundIndex(def = "{'members': 1, 'type': 1}")
@CompoundIndex(name = "idx_members_lastmsgtime", def = "{'members': 1, 'lastMessageTime': -1}")
data class Conversation(
    @Id
    val id: String? = null,
    val type: Int = 0,                         // 參見 ConversationType: 0=私聊, 1=群聊, 2=特殊私聊
    @Indexed
    val members: List<String> = emptyList(),    // 参与者 UID 列表（Multikey Index）
    val lastMessageId: String? = null,
    val lastMessageContent: String? = null,
    val lastMessageContentType: Int = 0,
    val lastMessageSenderId: String? = null,
    val lastMessageTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // ── 群组基础信息 (type=1) ──
    val name: String? = null,
    val avatarUrl: String? = null,
    @Indexed
    val ownerId: String? = null,
    val adminIds: List<String> = emptyList(),

    // ── 加群 & 可见性 ──
    val joinMode: Int = 0,             // 0=不限制 1=成员拉人 2=仅管理员拉人
    val searchable: Boolean = true,
    val maxMembers: Int = 5000,

    // ── 消息规则 ──
    val historyVisible: Boolean = true,
    val muteAll: Boolean = false,
    val mutedMembers: List<String> = emptyList(),
    val blockLinks: Boolean = false,

    // ── 添加好友限制 ──
    val addFriendMode: Int = 0,       // 0=所有人 1=仅群主 2=仅管理员 3=仅群成员

    // ── 公开群组 ──
    val publicState: Int = 0,            // 0=默认, 2=公开, 3=公开置顶

    // ── 群公告 ──
    val announcement: String = "",
    val announcementUpdatedAt: Long = 0,
    val announcementUpdatedBy: String? = null,

    // ── 置顶消息（最多 5 条，按 pinnedAt 降序，最新在前）──
    val pinnedMessages: List<PinnedMessage> = emptyList()
)

data class PinnedMessage(
    val messageId: String,
    val content: String? = null,
    val contentType: Int = 0,
    val senderId: String? = null,
    val senderName: String? = null,
    val seqId: Long? = null,
    val pinnedBy: String,
    val pinnedAt: Long
)
