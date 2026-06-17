package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Per-device readSeqId for a (user, device, conversation) tuple.
 *
 * 独立 collection，不嵌入 UserConversation，避免同账号多设备写入放大。
 * 与 UserConversation.readSeqId 的区别：
 * - UserConversation.readSeqId：账号级，用于推送对方"你的消息被我读到 X"
 * - DeviceReadPoint.readSeqId：设备级，用于每设备独立的未读判断
 */
@Document(collection = "device_read_points")
@CompoundIndex(
    name = "idx_drp_unique",
    unique = true,
    def = "{'userId': 1, 'deviceId': 1, 'conversationId': 1}"
)
@CompoundIndex(
    name = "idx_drp_user_conv",
    def = "{'userId': 1, 'conversationId': 1}"
)
data class DeviceReadPoint(
    @Id val id: String? = null,
    @Indexed val userId: String,
    val deviceId: String,
    val conversationId: String,
    val readSeqId: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
