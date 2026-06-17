package com.layababateam.xinxiwang_backend.handler

import com.layababateam.xinxiwang_backend.service.AvUsageSegmentPort
import io.netty.channel.ChannelHandlerContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AvUsageSegmentHandler(
    private val avUsageSegmentPort: AvUsageSegmentPort,
) : MessageHandler {
    private val log = LoggerFactory.getLogger(AvUsageSegmentHandler::class.java)

    override val type: String = "av_usage_segment"

    override fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>) {
        val scope = data["scope"]?.toString() ?: "meeting"
        val width = (data["width"] as? Number)?.toInt() ?: 0
        val height = (data["height"] as? Number)?.toInt() ?: 0
        val available = data["available"] as? Boolean ?: (width > 0 && height > 0)
        val streamType = data["streamType"]?.toString()

        when (scope) {
            "meeting" -> handleMeetingSegment(userId, data, streamType, width, height, available)
            "call" -> handleCallSegment(userId, data, streamType, width, height, available)
            else -> log.debug("[AV用量] 忽略未知 scope={}, userId={}", scope, userId)
        }
    }

    private fun handleMeetingSegment(
        userId: String,
        data: Map<String, Any?>,
        streamType: String?,
        width: Int,
        height: Int,
        available: Boolean,
    ) {
        val meetingId = data["meetingId"]?.toString()
            ?: throw IllegalArgumentException("meetingId 不能为空")
        val sourceUserId = data["sourceUserId"]?.toString()
            ?: throw IllegalArgumentException("sourceUserId 不能为空")

        avUsageSegmentPort.updateMeetingVideoSegment(
            meetingId = meetingId,
            subscriberUserId = userId,
            sourceUserId = sourceUserId,
            streamType = streamType,
            width = width,
            height = height,
            available = available,
        )
        log.debug(
            "[AV用量] 会议视频段 meetingId={}, subscriber={}, source={}, stream={}, size={}x{}, available={}",
            meetingId,
            userId,
            sourceUserId,
            streamType,
            width,
            height,
            available,
        )
    }

    private fun handleCallSegment(
        userId: String,
        data: Map<String, Any?>,
        streamType: String?,
        width: Int,
        height: Int,
        available: Boolean,
    ) {
        val roomId = (data["roomId"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("roomId 不能为空")

        avUsageSegmentPort.updateCallVideoSegment(
            userId = userId,
            roomId = roomId,
            streamType = streamType,
            width = width,
            height = height,
            available = available,
        )
        log.debug(
            "[AV用量] 通话视频段 roomId={}, userId={}, stream={}, size={}x{}, available={}",
            roomId,
            userId,
            streamType,
            width,
            height,
            available,
        )
    }
}
