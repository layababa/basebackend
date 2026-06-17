package com.layababateam.xinxiwang_backend.service

/**
 * 正在输入临时信令契约。
 *
 * SDK 负责 WebSocket 消息解析和目标用户计算，业务侧负责会话查询与在线推送。
 */
interface TypingStatusPort {
    fun resolveTypingTargets(userId: String, conversationId: String): List<String>

    fun sendTypingStatus(userId: String, conversationId: String, targetUserIds: List<String>)
}
