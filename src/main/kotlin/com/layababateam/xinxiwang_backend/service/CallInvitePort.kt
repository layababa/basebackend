package com.layababateam.xinxiwang_backend.service

/**
 * 通话邀请 WebSocket 能力契约。
 *
 * SDK 负责 call_invite 协议入口和当前连接响应出口，业务侧负责通话会话、TRTC、离线唤醒和审计。
 */
interface CallInvitePort {
    fun handleCallInvite(
        userId: String,
        targetUserId: String,
        callType: Int,
        responseSink: CallInviteResponseSink,
    )
}

fun interface CallInviteResponseSink {
    fun send(data: Map<String, Any?>)
}
