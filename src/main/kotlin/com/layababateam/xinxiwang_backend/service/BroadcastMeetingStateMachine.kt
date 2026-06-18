package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.BroadcastMeeting
import com.layababateam.xinxiwang_backend.model.BroadcastStatus

/**
 * 宣讲会状态机辅助，集中维护状态变更时的公共字段更新。
 */
object BroadcastMeetingStateMachine {
    fun canTransition(from: String, to: BroadcastStatus): Boolean =
        runCatching { BroadcastStatus.valueOf(from) }
            .getOrNull()
            ?.let { BroadcastStatus.canTransition(it, to) }
            ?: false

    fun transition(
        meeting: BroadcastMeeting,
        to: BroadcastStatus,
        updatedAt: Long = System.currentTimeMillis(),
    ): BroadcastMeeting {
        val from = BroadcastStatus.valueOf(meeting.status)
        require(BroadcastStatus.canTransition(from, to)) {
            "非法状态转移: $from -> $to"
        }
        return meeting.copy(status = to.name, updatedAt = updatedAt)
    }
}
