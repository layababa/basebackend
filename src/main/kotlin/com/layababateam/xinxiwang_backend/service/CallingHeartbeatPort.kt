package com.layababateam.xinxiwang_backend.service

/**
 * 通话中客户端心跳契约。
 *
 * SDK 复用 WebSocket 消息入口；心跳存储和过期策略由接入方实现。
 */
interface CallingHeartbeatPort {
    fun updateCallingHeartbeat(userId: String, roomId: Int)
}
