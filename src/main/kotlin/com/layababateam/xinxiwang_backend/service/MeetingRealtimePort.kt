package com.layababateam.xinxiwang_backend.service

/**
 * 宣讲会实时信令能力契约。
 *
 * SDK 负责 WebSocket 消息类型和基础参数入口，业务侧实现会议状态、权限、推送和消息落库细节。
 */
interface MeetingRealtimePort {
    fun sendChatMessage(userId: String, meetingId: String, content: String)

    fun handlePermissionSignal(
        userId: String,
        meetingId: String,
        action: String,
        data: Map<String, Any?>
    )

    fun shareMeeting(
        userId: String,
        meetingId: String,
        toConversationId: String,
        backendCompatVersion: Int
    )

    fun updateHeartbeat(userId: String, meetingId: String, roomId: Int)

    fun sendEmojiReaction(userId: String, meetingId: String, emoji: String)

    fun handleRaiseHand(
        userId: String,
        meetingId: String,
        action: String,
        targetUserId: String?,
        userName: String
    )

    fun handleMuteControl(
        userId: String,
        meetingId: String,
        action: String,
        muteTypes: List<String>,
        targetUserId: String?,
        operatorName: String
    )

    fun handleLinkMic(
        userId: String,
        meetingId: String,
        action: String,
        data: Map<String, Any?>
    )
}
