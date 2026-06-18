package com.layababateam.xinxiwang_backend.service

/**
 * 宣讲会通知事件类型和离线推送 payload 规则。
 */
object BroadcastNotificationPayloads {
    const val TYPE_REMINDER = "broadcast.reminder"
    const val TYPE_STARTED = "broadcast.started"
    const val TYPE_CANCELLED = "broadcast.cancelled"

    fun alert(type: String, title: String): BroadcastNotificationAlert {
        return when (type) {
            TYPE_REMINDER -> BroadcastNotificationAlert("宣讲即将开始", "「$title」将于 15 分钟后开始")
            TYPE_STARTED -> BroadcastNotificationAlert("宣讲已开始", "「$title」已开播")
            TYPE_CANCELLED -> BroadcastNotificationAlert("宣讲已取消", "「$title」已被管理员取消")
            else -> BroadcastNotificationAlert("宣讲通知", title)
        }
    }

    fun customData(
        type: String,
        broadcastId: String,
        title: String,
        scheduledAt: Long?,
    ): Map<String, Any> = mapOf(
        "type" to type,
        "broadcastId" to broadcastId,
        "title" to title,
        "scheduledAt" to (scheduledAt ?: 0L),
    )
}

data class BroadcastNotificationAlert(
    val title: String,
    val body: String,
)
