package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "red_packets")
@CompoundIndex(def = "{'expiredAt': 1, 'refunded': 1, 'remainingCount': 1}")
data class RedPacket(
    @Id
    val id: String? = null,
    val senderId: String,              // 发送者 ID
    val senderName: String = "",       // 发送者昵称
    @Indexed
    val conversationId: String,        // 所属会话
    val type: Int = 0,                 // 0=普通(一对一) 1=拼手气 2=平均 3=指定
    val totalAmount: String,           // 总金额
    val count: Int = 1,                // 红包个数
    val remainingAmount: String,       // 剩余金额
    val remainingCount: Int,           // 剩余个数
    val greeting: String = "恭喜发财，大吉大利",  // 祝福语
    val targetUserId: String? = null,  // type=3时指定用户
    val claimedBy: List<ClaimRecord> = emptyList(), // 领取记录
    val messageId: String? = null,     // 关联的聊天消息ID
    val refunded: Boolean = false,     // 是否已退款
    val createdAt: Long = System.currentTimeMillis(),
    val expiredAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000 // 默认24小时过期
)

data class ClaimRecord(
    val userId: String,
    val userName: String = "",
    val amount: String,
    val claimedAt: Long = System.currentTimeMillis()
)
