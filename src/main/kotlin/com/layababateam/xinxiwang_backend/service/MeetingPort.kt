package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.MeetingChatMessageDto
import com.layababateam.xinxiwang_backend.dto.MeetingDto
import com.layababateam.xinxiwang_backend.dto.MeetingJoinResponse
import com.layababateam.xinxiwang_backend.dto.MeetingRemovalRestrictionDto
import com.layababateam.xinxiwang_backend.dto.MeetingShareSnapshotRequest
import com.layababateam.xinxiwang_backend.dto.MeetingShareSnapshotResponse
import com.layababateam.xinxiwang_backend.dto.ParticipantDto

/**
 * 宣讲会会议业务能力契约。
 *
 * 控制器留在 SDK 中复用路由和响应格式；业务侧实现此 Port 以承接具体存储、TRTC、推送等细节。
 */
interface MeetingPort {
    fun createMeeting(
        userId: String,
        title: String,
        type: Int,
        password: String?,
        allowChat: Boolean,
        allowLinkMic: Boolean,
        allowScreenShare: Boolean,
        closeExisting: Boolean
    ): Pair<MeetingDto, String>

    fun scheduleMeeting(
        userId: String,
        title: String,
        password: String?,
        startAt: Long,
        durationMinutes: Int?,
        timeZone: String?,
        recurring: Boolean,
        recurringRule: String?,
        invitedUserIds: List<String>,
        remark: String?,
        allowJoinAfterStart: Boolean,
        allowChat: Boolean,
        allowLinkMic: Boolean,
        allowScreenShare: Boolean
    ): MeetingDto

    fun updateScheduledMeeting(
        userId: String,
        meetingId: String,
        title: String?,
        password: String?,
        startAt: Long?,
        durationMinutes: Int?,
        timeZone: String?,
        recurring: Boolean?,
        recurringRule: String?,
        invitedUserIds: List<String>?,
        remark: String?,
        allowJoinAfterStart: Boolean,
        allowChat: Boolean,
        allowLinkMic: Boolean,
        allowScreenShare: Boolean
    ): MeetingDto

    fun updateScheduledMeetingSettings(
        userId: String,
        meetingId: String,
        allowChat: Boolean?,
        allowLinkMic: Boolean?,
        allowJoinAfterStart: Boolean?
    ): MeetingDto

    fun cancelScheduledMeeting(userId: String, meetingId: String): MeetingDto

    fun extendMeeting(userId: String, meetingId: String, extraMinutes: Int): MeetingDto

    fun startScheduledMeeting(
        userId: String,
        meetingId: String,
        password: String?,
        inviteToken: String?
    ): MeetingJoinResponse

    fun getWaitingMeetingById(userId: String, meetingId: String): MeetingDto

    fun getWaitingMeetingByCode(userId: String, code: String): MeetingDto

    fun getWaitingMeetingWithShareSnapshot(
        userId: String,
        meetingId: String,
        req: MeetingShareSnapshotRequest
    ): MeetingShareSnapshotResponse

    fun getReservationMeetingById(userId: String, meetingId: String): MeetingDto

    fun getReservationMeetingByCode(userId: String, code: String): MeetingDto

    fun getReservationMeetingWithShareSnapshot(
        userId: String,
        meetingId: String,
        req: MeetingShareSnapshotRequest
    ): MeetingShareSnapshotResponse

    fun joinMeeting(
        userId: String,
        meetingId: String,
        password: String?,
        fromInvite: Boolean,
        inviteToken: String?
    ): MeetingJoinResponse

    fun joinByCode(
        userId: String,
        meetingCode: String,
        password: String?,
        inviteToken: String?
    ): MeetingJoinResponse

    fun leaveMeeting(userId: String, meetingId: String)

    fun endMeeting(userId: String, meetingId: String)

    fun getMeeting(userId: String, meetingId: String, includeScheduledFields: Boolean): MeetingDto

    fun getParticipants(userId: String, meetingId: String): List<ParticipantDto>

    fun getChatHistory(userId: String, meetingId: String): List<MeetingChatMessageDto>

    fun kickParticipant(operatorId: String, meetingId: String, targetUserId: String)

    fun getRemovalRestrictions(operatorId: String, meetingId: String): List<MeetingRemovalRestrictionDto>

    fun restoreRemovedParticipant(operatorId: String, meetingId: String, targetUserId: String)

    fun lockMeeting(userId: String, meetingId: String)

    fun unlockMeeting(userId: String, meetingId: String)

    fun setMeetingAdmin(operatorId: String, meetingId: String, targetUserId: String, isAdmin: Boolean): MeetingDto

    fun getUserMeetingHistory(
        userId: String,
        page: Int,
        size: Int,
        includeScheduledMeetings: Boolean
    ): Pair<List<MeetingDto>, Long>
}
