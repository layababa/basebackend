package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.BarrageCheckResponse
import com.layababateam.xinxiwang_backend.dto.BroadcastCardSnapshotDto
import com.layababateam.xinxiwang_backend.dto.BroadcastDto
import com.layababateam.xinxiwang_backend.dto.BroadcastListItemDto
import com.layababateam.xinxiwang_backend.dto.BroadcastViewerDto
import com.layababateam.xinxiwang_backend.dto.CreateBroadcastRequest
import com.layababateam.xinxiwang_backend.dto.CreateLuckyBagRequest
import com.layababateam.xinxiwang_backend.dto.CreateRedPacketRequest
import com.layababateam.xinxiwang_backend.dto.EditBroadcastRequest
import com.layababateam.xinxiwang_backend.dto.JoinBroadcastResponse
import com.layababateam.xinxiwang_backend.dto.LinkMicStateDto
import com.layababateam.xinxiwang_backend.dto.LuckyBagDto
import com.layababateam.xinxiwang_backend.dto.RedPacketDto

/**
 * 宣讲会主能力端口。
 *
 * SDK 复用路由和响应结构，房间状态机、推送、红包福袋结算和权限校验由接入方实现。
 */
interface BroadcastPort {
    fun create(userId: String, request: CreateBroadcastRequest): BroadcastDto

    fun get(userId: String, broadcastId: String): BroadcastDto

    fun subscribe(userId: String, broadcastId: String)

    fun unsubscribe(userId: String, broadcastId: String)

    fun list(scope: String, page: Int, pageSize: Int): BroadcastPage<BroadcastListItemDto>

    fun join(userId: String, broadcastId: String, password: String?, forceLeaveOther: Boolean): JoinBroadcastResponse

    fun leave(userId: String, broadcastId: String)

    fun startStreaming(userId: String, broadcastId: String): BroadcastDto

    fun end(userId: String, broadcastId: String): BroadcastDto

    fun cancel(userId: String, broadcastId: String): BroadcastDto

    fun edit(userId: String, broadcastId: String, request: EditBroadcastRequest): BroadcastDto

    fun viewers(
        broadcastId: String,
        page: Int,
        pageSize: Int,
        filter: String?,
        keyword: String?,
    ): List<BroadcastViewerDto>

    fun kick(operatorId: String, broadcastId: String, targetUserId: String): BroadcastDto

    fun mute(operatorId: String, broadcastId: String, targetUserId: String): BroadcastDto

    fun unmute(operatorId: String, broadcastId: String, targetUserId: String): BroadcastDto

    fun muteAll(operatorId: String, broadcastId: String): BroadcastDto

    fun unmuteAll(operatorId: String, broadcastId: String): BroadcastDto

    fun transferSpeaker(operatorId: String, broadcastId: String, newSpeakerId: String): BroadcastDto

    fun raiseHand(userId: String, broadcastId: String): Int

    fun cancelRaiseHand(userId: String, broadcastId: String)

    fun approveLinkMic(operatorId: String, broadcastId: String, targetUserId: String)

    fun rejectLinkMic(operatorId: String, broadcastId: String, targetUserId: String, reason: String?)

    fun removeLinkMic(operatorId: String, broadcastId: String, targetUserId: String)

    fun linkMicState(broadcastId: String): LinkMicStateDto

    fun checkBarrage(userId: String, broadcastId: String, content: String): BarrageCheckResponse

    fun incrementLikes(broadcastId: String, count: Int)

    fun createRedPacket(userId: String, broadcastId: String, request: CreateRedPacketRequest): RedPacketDto

    fun createLuckyBag(userId: String, broadcastId: String, request: CreateLuckyBagRequest): LuckyBagDto

    fun cardSnapshots(ids: List<String>): List<BroadcastCardSnapshotDto>
}

data class BroadcastPage<T>(
    val total: Long,
    val content: List<T>,
)
