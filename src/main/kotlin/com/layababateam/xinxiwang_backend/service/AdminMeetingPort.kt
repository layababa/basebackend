package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.MeetingDto
import com.layababateam.xinxiwang_backend.dto.MeetingJoinResponse
import com.layababateam.xinxiwang_backend.dto.ParticipantDto

interface AdminMeetingPort {
    fun getAllMeetings(page: Int, size: Int, status: Int?, keyword: String?): Pair<List<MeetingDto>, Long>

    fun getMeeting(meetingId: String): MeetingDto

    fun getParticipants(meetingId: String): List<ParticipantDto>

    fun getHistoricalParticipants(meetingId: String): List<ParticipantDto>

    fun forceEndMeeting(meetingId: String)

    fun adminJoinMeeting(adminUserId: String, meetingId: String): MeetingJoinResponse
}
