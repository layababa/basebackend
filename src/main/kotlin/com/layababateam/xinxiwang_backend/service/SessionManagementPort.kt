package com.layababateam.xinxiwang_backend.service

import io.netty.channel.Channel

/**
 * 多端设备会话管理契约。
 *
 * SDK 复用 WebSocket 消息入口和响应格式；设备会话存储、Token 失效和连接踢下线由接入方实现。
 */
interface SessionManagementPort {
    fun listActiveSessions(userId: String, currentChannel: Channel): List<Map<String, Any?>>

    fun terminateSession(userId: String, currentChannel: Channel, sessionId: String)

    fun terminateAllOtherSessions(userId: String, currentChannel: Channel): Int
}
