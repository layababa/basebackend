package com.layababateam.xinxiwang_backend.service

/**
 * 消息送达 ACK 契约。
 *
 * SDK 复用 WebSocket ACK 消息入口；ACK 状态存储、重试和裁剪策略由接入方实现。
 */
interface MessageAckPort {
    fun confirmAck(userId: String, seqId: Long)
}
