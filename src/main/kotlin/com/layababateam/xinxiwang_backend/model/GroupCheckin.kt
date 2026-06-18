package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 群签到（一个群里发起的连续签到活动）。
 *
 * 签到卡片以 contentType=20 的聊天消息形式出现在会话中（messageId 关联）。
 * 完整版能力：连续签到 + 轮次 + 断签重置 + 每日积分 + 节点奖励。
 * 签到/关闭通过 WS 实时广播 group_checkin_updated。
 *
 * 规则字段（roundDays/dailyPoints/rewardNodes/...）在创建时从 admin 配置或
 * 默认值快照进来；运行态 records 是每日签到事件流（一个用户一天最多一条）。
 */
@Document(collection = "group_checkins")
data class GroupCheckin(
    @Id val id: String? = null,
    @Indexed val conversationId: String,
    val creatorId: String,
    val creatorName: String,
    /** 标题/主题 上限 50 字 */
    val title: String,
    /** 说明 上限 500 字 */
    val description: String? = null,
    /** 0=进行中, 1=已结束 */
    val status: Int = 0,
    /** 关联的签到卡片消息 id */
    val messageId: String? = null,

    // ===== 规则配置（创建时快照） =====
    /** 一轮总天数（轮次），后台可配 */
    val roundDays: Int = DEFAULT_ROUND_DAYS,
    /** 每日签到积分 */
    val dailyPoints: Int = DEFAULT_DAILY_POINTS,
    /** 连续奖励节点，按 day 升序 */
    val rewardNodes: List<RewardNode> = DEFAULT_REWARD_NODES,
    /** 活动开始(ms)；null=立即 */
    val startAt: Long? = null,
    /** 活动结束(ms)；null=长期 */
    val endAt: Long? = null,
    /** 是否默认 @所有人 */
    val mentionAll: Boolean = true,
    /** 是否展示群聊顶部专属入口 */
    val showTopEntry: Boolean = true,

    // ===== 运行态 =====
    /** 每日签到事件流（见 CheckinRecord） */
    val records: List<CheckinRecord> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_ROUND_DAYS = 30
        const val DEFAULT_DAILY_POINTS = 1
        val DEFAULT_REWARD_NODES: List<RewardNode> = listOf(
            RewardNode(7, 5),
            RewardNode(15, 10),
            RewardNode(30, 30),
        )
    }
}

/** 连续第 [day] 天，额外奖励 [points] 分。 */
data class RewardNode(
    val day: Int,
    val points: Int,
)

/**
 * 单次签到事件（一个用户一天最多一条）。
 *
 * streakId：连续周期序号（活动内自增，从 1 起）。首签 / 断签重置 / 进新轮 → 开启新周期。
 * round：本次签到所属轮次（从 1 起）。
 * consecutiveDays：本次签到后的连续天数（1..roundDays）。
 */
data class CheckinRecord(
    val userId: String,
    val userName: String,
    /** 可选留言（本需求签到不强制填写内容） */
    val content: String? = null,
    /** "yyyyMMdd" 服务器时区自然日 */
    val signDate: String,
    /** 连续周期序号（活动内自增） */
    val streakId: Int,
    /** 本次签到所属轮次（从 1 起） */
    val round: Int,
    /** 本次签到后的连续天数（1..roundDays） */
    val consecutiveDays: Int,
    /** 本次每日积分 */
    val dailyPoints: Int,
    /** 本次触发的节点奖励（未触发=0） */
    val nodePoints: Int,
    /** dailyPoints + nodePoints */
    val totalPoints: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
