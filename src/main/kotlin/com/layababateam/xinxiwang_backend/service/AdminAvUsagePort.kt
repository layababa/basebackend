package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AvUsageOverviewDto
import com.layababateam.xinxiwang_backend.dto.MeetingAvUsageStatsDto

/**
 * 后台音视频用量统计端口。
 *
 * SDK 复用后台 HTTP 契约，TRTC 用量估算、会议段统计和账单口径由接入方实现。
 */
interface AdminAvUsagePort {
    fun getOverview(days: Int): AvUsageOverviewDto

    @Throws(IllegalArgumentException::class)
    fun getMeetingStats(meetingId: String): MeetingAvUsageStatsDto
}
