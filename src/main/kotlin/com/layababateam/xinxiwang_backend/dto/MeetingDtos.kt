package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Size

data class CreateMeetingRequest(
    @field:Size(max = 50, message = "会议标题不能超过50个字符")
    val title: String,
    val type: Int = 0,
    val password: String? = null
)

data class MeetingDto(
    val id: String,
    val meetingCode: String,
    val title: String,
    val creatorId: String,
    val creatorName: String?,
    val roomId: Int,
    val type: Int,
    val status: Int,
    @get:JsonProperty("isLocked")
    val isLocked: Boolean,
    val hasPassword: Boolean,
    val participantCount: Int,
    val createdAt: Long,
    val endedAt: Long? = null
)

data class MeetingJoinResponse(
    val meetingId: String,
    val meetingCode: String,
    val roomId: Int,
    val userSig: String,
    val sdkAppId: Long,
    val title: String,
    val creatorId: String,
    val creatorName: String?,
    val type: Int,
    val isLocked: Boolean
)

data class JoinByCodeRequest(
    val meetingCode: String,
    val password: String? = null
)

data class JoinByIdRequest(
    val password: String? = null
)

data class MeetingChatMessageDto(
    val meetingId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long
)
