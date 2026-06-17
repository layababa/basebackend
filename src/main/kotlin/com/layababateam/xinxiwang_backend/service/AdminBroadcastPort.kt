package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.BroadcastDto
import com.layababateam.xinxiwang_backend.model.BroadcastLuckyBag
import com.layababateam.xinxiwang_backend.model.BroadcastMeeting
import com.layababateam.xinxiwang_backend.model.BroadcastParticipant
import com.layababateam.xinxiwang_backend.model.BroadcastRedPacket
import com.layababateam.xinxiwang_backend.model.BroadcastRedPacketGrab

/**
 * 后台宣讲会管理端口。
 *
 * SDK 只抽取后台路由契约，具体查询、统计口径和强制结束/取消副作用由接入方提供。
 */
interface AdminBroadcastPort {
    fun list(
        status: String?,
        creatorId: String?,
        speakerId: String?,
        keyword: String?,
        page: Int,
        pageSize: Int,
    ): BroadcastPage<BroadcastMeeting>

    fun detail(broadcastId: String): BroadcastMeeting

    fun forceEnd(adminUserId: String, broadcastId: String): BroadcastDto

    fun forceCancel(adminUserId: String, broadcastId: String): BroadcastDto

    fun viewers(broadcastId: String, page: Int, pageSize: Int): BroadcastPage<BroadcastParticipant>

    fun redPackets(broadcastId: String): List<BroadcastRedPacket>

    fun redPacketGrabs(redPacketId: String): List<BroadcastRedPacketGrab>

    fun luckyBags(broadcastId: String): List<BroadcastLuckyBag>

    fun stats(broadcastId: String): AdminBroadcastStats
}

data class AdminBroadcastStats(
    val title: String,
    val status: String,
    val peakViewerCount: Int,
    val totalParticipants: Int,
    val onMicCount: Int,
    val likeCount: Long,
    val redPacketCount: Int,
    val redPacketTotalGrabbed: Long,
    val redPacketDistributedPoints: Long,
    val redPacketRefundedPoints: Long,
    val startedAt: Long?,
    val endedAt: Long?,
    val durationMs: Long?,
)
