package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 群签到规则模板（admin 为某个群预配置的默认规则）。
 *
 * 用途：群主/管理员客户端走 WS `create_checkin` 发起签到时，若该群存在本模板，
 * 则用模板规则快照创建活动；否则用 [GroupCheckin] 内置默认值。
 *
 * 注意：这是"规则模板"，不是活动本身。admin 直接建活动走 GroupCheckin。
 */
@Document(collection = "checkin_group_configs")
data class CheckinGroupConfig(
    @Id
    @Indexed(unique = true)
    val conversationId: String,
    val roundDays: Int = GroupCheckin.DEFAULT_ROUND_DAYS,
    val dailyPoints: Int = GroupCheckin.DEFAULT_DAILY_POINTS,
    val rewardNodes: List<RewardNode> = GroupCheckin.DEFAULT_REWARD_NODES,
    val mentionAll: Boolean = true,
    val showTopEntry: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)
