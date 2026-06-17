package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.GrabRedPacketResponse
import com.layababateam.xinxiwang_backend.dto.RedPacketDto

/**
 * 宣讲场内红包开放能力。
 *
 * SDK 复用路由与响应格式，业务侧负责积分、并发扣减和推送实现。
 */
interface BroadcastRedPacketPort {
    fun grab(userId: String, redPacketId: String): GrabRedPacketResponse

    fun get(redPacketId: String, callerUserId: String): RedPacketDto
}
