package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.ConversationType
import org.springframework.stereotype.Component

/**
 * 群组禁言策略 — 单一权威判定。
 *
 * 任何会向群组写入消息 / 创建红包 / 转账等"发送性"动作的入口都必须经过 [requireSendable]，
 * 否则将出现"钱扣了消息没发出"等事务一致性事故（参考 2026-05-21 06:17:45 事故）。
 *
 * 规则：
 * - 私聊 (`type != GROUP`) 一律放行
 * - 非群成员 → 抛 "您不在此群"
 * - 单人禁言 (`senderId in mutedMembers`) → 抛 "群组已禁止聊天"（含管理员也生效）
 * - 全员禁言 (`muteAll`) 且非群主/管理员 → 抛 "群组已禁止聊天"
 *
 * 用户可见提示统一为 "群组已禁止聊天"，不区分单人/全员（按产品规范）。
 *
 * 注意：替换调用方现有 inline 禁言检查时，**必须同时删除**旧的 inline 块
 * （例如 MessageService.kt:117-125），否则生产会同时出现 "全员禁言中"/"您已被禁言"
 * 与 "群组已禁止聊天" 两套文案。
 */
@Component
class GroupMutePolicy {

    fun requireSendable(conversation: Conversation, senderId: String) {
        if (conversation.type != ConversationType.GROUP.value) return
        // All branches throw IllegalStateException uniformly so callers can use a single catch site
        // for "cannot send right now" — chosen over Kotlin's require/check idiom on purpose.
        if (senderId !in conversation.members) throw IllegalStateException("您不在此群")
        if (senderId in conversation.mutedMembers) throw IllegalStateException("群组已禁止聊天")
        if (conversation.muteAll && !isGroupManager(conversation, senderId)) {
            throw IllegalStateException("群组已禁止聊天")
        }
    }

    private fun isGroupManager(conversation: Conversation, senderId: String): Boolean =
        senderId == conversation.ownerId || senderId in conversation.adminIds
}
