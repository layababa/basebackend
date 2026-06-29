package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ========== 消息相关 ==========

data class SendMessageRequest(
    @field:NotBlank(message = "會話識別碼不可為空白")
    val conversationId: String,

    @field:NotBlank(message = "訊息內容不可為空白")
    @field:Size(max = 5000, message = "訊息內容不可超過 5000 個字元")
    val content: String,

    @field:Min(value = 0, message = "內容類型最小為 0")
    @field:Max(value = 13, message = "內容類型最大為 13")
    val contentType: Int = 0   // 0=text, 1=image, 2=voice, 3=video, 4=file, 5=red_packet, 10=system, 11=transfer, 12=call, 13=business_card
)

data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String? = null,
    val senderAvatar: String? = null,
    val contentType: Int,
    val content: String,
    val seqId: Long,
    @get:JsonProperty("isRecalled")
    val isRecalled: Boolean = false,
    val mentions: List<String> = emptyList(),
    val createdAt: Long,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val clientMessageId: String? = null,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val replyTo: ReplyToDto? = null,
    val senderRole: Int = 0,  // 0=member, 1=admin, 2=owner
    val senderIsBot: Boolean = false,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val groupName: String? = null,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val groupAvatar: String? = null
)

data class ReplyToDto(
    val messageId: String,
    val senderId: String,
    val senderName: String? = null,
    val content: String,
    val contentType: Int = 0,
    @get:JsonProperty("isRecalled")
    val isRecalled: Boolean = false
)

// ========== 会话相关 ==========

data class ConversationDto(
    val id: String,
    val type: Int,
    val peerUserId: String? = null,
    val peerUserName: String? = null,
    val peerUserAvatar: String? = null,
    val peerIsBot: Boolean = false,
    val lastMessageContent: String? = null,
    val lastMessageContentType: Int = 0,
    val lastMessageSenderId: String? = null,
    val lastMessageTime: Long? = null,
    /** 群聊列表预览：最后一条消息发送者显示名（与 lastMessage* 一并由服务端下发） */
    val lastMessageSenderName: String? = null,
    val unreadCount: Long = 0,
    val readSeqId: Long = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val createdAt: Long,

    // ── 群组字段 (type=1) ──
    val groupName: String? = null,
    val groupAvatar: String? = null,
    val ownerId: String? = null,
    val memberCount: Int = 0,
    val muteAll: Boolean = false,
    val blockLinks: Boolean = false,
    val announcement: String? = null,
    val myNickname: String? = null,
    val groupRemark: String? = null,
    val savedToContacts: Boolean = false,
    val mentionedSeqIds: List<Long> = emptyList(),
    val peerRemark: String? = null,
    val joinMode: Int = 0,
    val addFriendMode: Int = 0,
    val searchable: Boolean = true,
    val historyVisible: Boolean = true,
    val adminIds: List<String> = emptyList(),

    // ── 置顶消息（最多 5 条）──
    val pinnedMessages: List<PinnedMessageDto> = emptyList(),

    // ── 服务端最大 seqId（用于客户端判断是否需要同步）──
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val serverMaxSeqId: Long? = null,

    // ── 请求方当前设备的独立已读位点（仅在 ws session 能识别 deviceId 时填充）──
    // 新客户端用它替代 readSeqId 判断未读，旧客户端反序列化会忽略
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val myDeviceReadSeqId: Long? = null
)

data class PinnedMessageDto(
    val messageId: String,
    val content: String? = null,
    val contentType: Int = 0,
    val senderName: String? = null,
    val seqId: Long? = null,
    val pinnedBy: String,
    val pinnedAt: Long
)

// ========== 好友相关 ==========

data class UserSummaryDto(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val gender: Int,
    val bio: String,
    @get:JsonProperty("isBot")
    val isBot: Boolean = false,
    @get:JsonProperty("isOperator")
    val isOperator: Boolean = false
)

data class FriendRequestSendDto(
    @field:NotBlank(message = "目標使用者識別碼不可為空白")
    val toUserId: String,

    @field:Size(max = 200, message = "附帶訊息不可超過 200 個字元")
    val message: String = "",

    val fromGroupId: String? = null,

    val sourceCardMessageId: String? = null
)

data class FriendAcceptDto(
    @field:NotBlank(message = "請求識別碼不可為空白")
    val requestId: String
)

data class FriendRejectDto(
    @field:NotBlank(message = "請求識別碼不可為空白")
    val requestId: String,
    val permanent: Boolean = false
)

data class FriendDeleteDto(
    @field:NotBlank(message = "好友識別碼不可為空白")
    val friendId: String
)

data class FriendBlockDto(
    @field:NotBlank(message = "好友識別碼不可為空白")
    val friendId: String
)

data class FriendUnblockDto(
    @field:NotBlank(message = "好友識別碼不可為空白")
    val friendId: String
)

data class FriendRequestDto(
    val id: String,
    val fromUserId: String,
    val fromUserDisplayName: String? = null,
    val fromUserUsername: String? = null,
    val fromUserAvatarUrl: String? = null,
    val toUserId: String,
    val message: String,
    val status: Int,
    val createdAt: Long,
    val updatedAt: Long = 0,
    @get:JsonProperty("isOutgoing")
    val isOutgoing: Boolean = false
)

data class FriendDto(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val gender: Int,
    val bio: String,
    val conversationId: String,
    val remark: String,
    val version: Long = 0
)

data class FriendSyncResponse(
    val friends: List<FriendDto>,
    val allFriendIds: List<String>,
    val latestVersion: Long,
    val hasMore: Boolean
)
