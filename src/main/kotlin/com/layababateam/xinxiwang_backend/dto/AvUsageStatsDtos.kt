package com.layababateam.xinxiwang_backend.dto

data class TrtcWeightRuleDto(
    val tier: String,
    val label: String,
    val weight: Int,
    val note: String,
)

data class AvWeightedUsageDto(
    val rawSeconds: Long,
    val weightedSeconds: Long,
    val rawMinutes: Long,
    val weightedMinutes: Long,
    val weight: Int,
    val tier: String,
    val label: String,
)

data class AvUsageOverviewDto(
    val windowDays: Int,
    val generatedAt: Long,
    val calls: AvUsageBreakdownDto,
    val meetings: AvUsageBreakdownDto,
    val totalWeightedSeconds: Long,
    val totalWeightedMinutes: Long,
    val weightRules: List<TrtcWeightRuleDto>,
    val notes: List<String>,
)

data class AvUsageBreakdownDto(
    val audio: AvWeightedUsageDto,
    val video: AvWeightedUsageDto,
    val tierBreakdown: List<AvWeightedUsageDto>,
    val totalRawSeconds: Long,
    val totalWeightedSeconds: Long,
    val totalRawMinutes: Long,
    val totalWeightedMinutes: Long,
    val recordCount: Long,
    val estimated: Boolean,
)

data class MeetingAvUsageStatsDto(
    val meetingId: String,
    val roomId: Int,
    val status: Int,
    val generatedAt: Long,
    val participantCount: Int,
    val segmentCount: Int,
    val activeSegmentCount: Int,
    val audio: AvWeightedUsageDto,
    val video: AvWeightedUsageDto,
    val tierBreakdown: List<AvWeightedUsageDto>,
    val totalRawSeconds: Long,
    val totalWeightedSeconds: Long,
    val totalRawMinutes: Long,
    val totalWeightedMinutes: Long,
    val estimated: Boolean,
    val participantSegments: List<MeetingParticipantAvSegmentDto>,
    val weightRules: List<TrtcWeightRuleDto>,
    val notes: List<String>,
)

data class MeetingParticipantAvSegmentDto(
    val userId: String,
    val sourceUserId: String?,
    val joinedAt: Long,
    val leftAt: Long?,
    val durationSeconds: Long,
    val weightedSeconds: Long,
    val rawMinutes: Long,
    val weightedMinutes: Long,
    val active: Boolean,
    val mediaType: String,
    val streamType: String?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val tier: String,
    val label: String,
    val weight: Int,
    val estimated: Boolean,
)
