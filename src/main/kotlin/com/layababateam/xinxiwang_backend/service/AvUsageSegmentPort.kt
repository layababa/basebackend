package com.layababateam.xinxiwang_backend.service

/**
 * 音视频用量分段上报契约。
 *
 * SDK 复用 WebSocket 消息入口；会议和通话用量段的落库、聚合策略由接入方实现。
 */
interface AvUsageSegmentPort {
    fun updateMeetingVideoSegment(
        meetingId: String,
        subscriberUserId: String,
        sourceUserId: String,
        streamType: String?,
        width: Int,
        height: Int,
        available: Boolean,
    )

    fun updateCallVideoSegment(
        userId: String,
        roomId: Int,
        streamType: String?,
        width: Int,
        height: Int,
        available: Boolean,
    )
}
