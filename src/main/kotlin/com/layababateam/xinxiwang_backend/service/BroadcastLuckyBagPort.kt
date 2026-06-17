package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.LuckyBagDto

/**
 * 宣讲场内福袋开放能力。
 *
 * SDK 只定义入参与响应边界，抽奖、积分和房间推送仍由接入方实现。
 */
interface BroadcastLuckyBagPort {
    fun join(userId: String, luckyBagId: String): LuckyBagDto

    fun get(luckyBagId: String, callerUserId: String): LuckyBagDto
}
