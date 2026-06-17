package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Size

data class CreateMeetingRequest(
    @field:Size(max = 50, message = "会议标题不能超过50个字符")
    val title: String,
    val type: Int = 0,
    val password: String? = null,
    val allowChat: Boolean? = null,
    val allowLinkMic: Boolean? = null,
    val allowScreenShare: Boolean? = null,
    val closeExisting: Boolean? = null,
)

data class MeetingPermissionSettingsDto(
    val allowChat: Boolean = false,
    val allowLinkMic: Boolean = false,
    val allowScreenShare: Boolean = true,
    val deniedChatUsers: List<String> = emptyList(),
    val deniedLinkMicUsers: List<String> = emptyList(),
    val deniedScreenShareUsers: List<String> = emptyList(),
    val linkMicLockedByQuickAction: Boolean = false,
    val quickActionPreviousAllowChat: Boolean? = null,
    val quickActionPreviousAllowLinkMic: Boolean? = null,
)

data class ScheduleMeetingRequest(
    @field:Size(max = 50, message = "会议标题不能超过50个字符")
    val title: String,
    val password: String? = null,
    val startAt: Long,
    val durationMinutes: Int? = null,
    val timeZone: String = "GMT+08:00",
    val recurring: Boolean = false,
    val recurringRule: String? = null,
    val invitedUserIds: List<String> = emptyList(),
    val remark: String? = null,
    val allowJoinAfterStart: Boolean? = null,
    val allowChat: Boolean? = null,
    val allowLinkMic: Boolean? = null,
    val allowScreenShare: Boolean? = null,
)

data class UpdateScheduleMeetingRequest(
    @field:Size(max = 50, message = "会议标题不能超过50个字符")
    val title: String,
    val password: String? = null,
    val startAt: Long,
    val durationMinutes: Int? = null,
    val timeZone: String = "GMT+08:00",
    val recurring: Boolean = false,
    val recurringRule: String? = null,
    val invitedUserIds: List<String> = emptyList(),
    val remark: String? = null,
    val allowJoinAfterStart: Boolean? = null,
    val allowChat: Boolean? = null,
    val allowLinkMic: Boolean? = null,
    val allowScreenShare: Boolean? = null,
)

data class UpdateScheduleSettingsRequest(
    val allowChat: Boolean? = null,
    val allowLinkMic: Boolean? = null,
    val allowJoinAfterStart: Boolean? = null,
)

data class ExtendMeetingRequest(
    val extraMinutes: Int = 30,
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
    val adminIds: List<String> = emptyList(),
    @get:JsonProperty("isLocked")
    val isLocked: Boolean,
    val hasPassword: Boolean,
    val permissionSettings: MeetingPermissionSettingsDto = MeetingPermissionSettingsDto(),
    val activeScreenShareUserId: String? = null,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,
    val scheduledTimeZone: String? = null,
    val recurring: Boolean = false,
    val recurringRule: String = "never",
    val invitedUserIds: List<String> = emptyList(),
    val remark: String? = null,
    val allowJoinAfterStart: Boolean = true,
    val scheduleVersion: Long = 0,
    val passwordVersion: Long = 0,
    val scheduleUpdatedAt: Long? = null,
    val canceledAt: Long? = null,
    val managementOwnerId: String? = null,
    val startedAt: Long? = null,
    val durationPromptSentAt: Long? = null,
    val password: String? = null,
    val inviteToken: String? = null,
    val participantCount: Int,
    val createdAt: Long,
    val endedAt: Long? = null,
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
    val adminIds: List<String> = emptyList(),
    val type: Int,
    val isLocked: Boolean,
    val permissionSettings: MeetingPermissionSettingsDto = MeetingPermissionSettingsDto(),
    val activeScreenShareUserId: String? = null,
    val scheduledStartAt: Long? = null,
    val scheduledDurationMinutes: Int? = null,
    val scheduledTimeZone: String? = null,
    val recurring: Boolean = false,
    val recurringRule: String = "never",
    val invitedUserIds: List<String> = emptyList(),
    val remark: String? = null,
    val allowJoinAfterStart: Boolean = true,
    val scheduleVersion: Long = 0,
    val passwordVersion: Long = 0,
    val scheduleUpdatedAt: Long? = null,
    val canceledAt: Long? = null,
    val managementOwnerId: String? = null,
    val startedAt: Long? = null,
    val inviteToken: String? = null,
    val createdAt: Long,
)

data class JoinByCodeRequest(
    val meetingCode: String,
    val password: String? = null,
    val inviteToken: String? = null,
)

data class JoinByIdRequest(
    val password: String? = null,
    val fromInvite: Boolean? = null,
    val inviteToken: String? = null,
)

data class MeetingShareSnapshotRequest(
    val sharedScheduleVersion: Long? = null,
    val sharedPassword: String? = null,
    val sharedPasswordVersion: Long? = null,
)

data class MeetingShareSnapshotResponse(
    val meeting: MeetingDto,
    val notice: String? = null,
)

data class SetMeetingAdminRequest(
    val userId: String,
    val isAdmin: Boolean,
)

data class MeetingChatMessageDto(
    val meetingId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
)
