package com.layababateam.xinxiwang_backend.service.push

object PushNotificationText {
    data class Text(
        val title: String,
        val body: String,
        val deepLink: String? = null,
        val customData: Map<String, Any> = emptyMap(),
    )

    fun from(type: String?, data: Map<*, *>?, appScheme: String? = null): Text? {
        if (type == null) return null
        return when (type) {
            "new_message" -> newMessage(data, appScheme)
            "friend_request_notification" -> {
                val fromName = string(data, "fromDisplayName", "fromUserDisplayName") ?: "有人"
                Text("好友请求", "${fromName}请求添加你为好友")
            }
            "friend_accepted_notification" -> {
                val name = string(data, "friendDisplayName", "displayName", "fromDisplayName") ?: "好友"
                Text("好友已通过", "${name}已通过你的好友请求")
            }
            "incoming_call" -> {
                val callerName = string(data, "callerName") ?: "有人"
                val callType = number(data, "callType") ?: 0
                val callTypeStr = if (callType == 1) "视频" else "语音"
                Text("来电", "${callerName}邀请你${callTypeStr}通话")
            }
            "broadcast.reminder" -> broadcast(type, data, appScheme, "宣讲提醒", "即将开始")
            "broadcast.started" -> broadcast(type, data, appScheme, "宣讲开始", "正在直播")
            "broadcast.cancelled" -> broadcast(type, data, appScheme, "宣讲取消", "已取消")
            else -> fallback(type, data)
        }
    }

    private fun newMessage(data: Map<*, *>?, appScheme: String?): Text {
        val senderName = string(data, "senderName") ?: "新消息"
        val groupName = string(data, "groupName")
        val contentType = number(data, "contentType") ?: 0
        val content = string(data, "content") ?: ""
        val convId = string(data, "conversationId")
        val isGroup = !groupName.isNullOrBlank()
        val bodyText = when (contentType) {
            0 -> content.take(100)
            1 -> "[图片]"
            2 -> "[语音]"
            3 -> "[视频]"
            4 -> "[文件]"
            5 -> "[通话]"
            6 -> "[系统通知]"
            7, 11 -> "[红包]"
            8 -> "[表情]"
            10 -> "[转账]"
            12 -> "[积分变动通知]"
            13 -> "[个人名片]"
            14 -> "[群聊名片]"
            16 -> "[会议]"
            else -> "[消息]"
        }
        val title = if (isGroup) groupName!! else senderName
        val body = if (isGroup) "$senderName: $bodyText" else bodyText
        val deepLink = if (appScheme != null && convId != null) "$appScheme://chat/$convId" else null
        return Text(title, body, deepLink, mapOfNotNull("conversationId" to convId, "type" to "new_message"))
    }

    private fun broadcast(type: String, data: Map<*, *>?, appScheme: String?, title: String, suffix: String): Text {
        val broadcastId = string(data, "broadcastId")
        val name = string(data, "title") ?: "宣讲大会"
        val deepLink = if (appScheme != null && broadcastId != null) "$appScheme://broadcast/$broadcastId" else null
        return Text(title, "$name $suffix", deepLink, mapOfNotNull("type" to type, "broadcastId" to broadcastId))
    }

    private fun fallback(type: String, data: Map<*, *>?): Text? {
        val title = string(data, "title", "subject") ?: type.takeIf { it.isNotBlank() }
        val body = string(data, "body", "content", "message", "detail") ?: return null
        return Text(title ?: "通知", body.take(120), customData = mapOf("type" to type))
    }

    private fun string(data: Map<*, *>?, vararg keys: String): String? =
        keys.asSequence()
            .mapNotNull { data?.get(it) as? String }
            .firstOrNull { it.isNotBlank() }

    private fun number(data: Map<*, *>?, key: String): Int? =
        (data?.get(key) as? Number)?.toInt()

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>): Map<String, Any> =
        pairs.mapNotNull { (key, value) -> if (value == null) null else key to value }.toMap()
}
