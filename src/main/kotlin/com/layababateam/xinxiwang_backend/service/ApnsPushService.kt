package com.layababateam.xinxiwang_backend.service

import com.eatthepath.pushy.apns.ApnsClient
import com.eatthepath.pushy.apns.ApnsClientBuilder
import com.eatthepath.pushy.apns.DeliveryPriority
import com.eatthepath.pushy.apns.PushType
import com.eatthepath.pushy.apns.auth.ApnsSigningKey
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder
import com.eatthepath.pushy.apns.util.InterruptionLevel
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification
import com.eatthepath.pushy.apns.util.TokenUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.DeviceSessionRepository
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class ApnsPushService(
    private val deviceSessionRepository: DeviceSessionRepository,
    private val userConversationRepository: UserConversationRepository,
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
    @Lazy private val conversationService: ConversationService,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
    @Value("\${apns.key-path:}") private val keyPath: String,
    @Value("\${apns.key-id:}") private val keyId: String,
    @Value("\${apns.team-id:}") private val teamId: String,
    @Value("\${apns.bundle-id:}") private val bundleId: String,
    @Value("\${apns.production:false}") private val production: Boolean
) {
    private val log = LoggerFactory.getLogger(ApnsPushService::class.java)
    private var apnsClient: ApnsClient? = null

    @PostConstruct
    fun init() {
        if (keyPath.isBlank() || keyId.isBlank() || teamId.isBlank()) {
            log.warn("APNs config incomplete (key-path, key-id, or team-id missing). Push disabled.")
            return
        }
        try {
            val resource = resourceLoader.getResource(keyPath)
            if (!resource.exists()) {
                log.warn("APNs key file not found at {}. Push disabled.", keyPath)
                return
            }

            val server = if (production)
                ApnsClientBuilder.PRODUCTION_APNS_HOST
            else
                ApnsClientBuilder.DEVELOPMENT_APNS_HOST

            apnsClient = resource.inputStream.use { inputStream ->
                ApnsClientBuilder()
                    .setApnsServer(server)
                    .setSigningKey(ApnsSigningKey.loadFromInputStream(inputStream, teamId, keyId))
                    .build()
            }
            log.debug("APNs client initialized (production={}, key={})", production, keyPath)
        } catch (e: Exception) {
            log.error("Failed to initialize APNs client", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        apnsClient?.close()?.get()
    }

    /**
     * Called when a user is offline on all WS nodes (legacy entry point).
     */
    fun pushOfflineNotification(userId: String, wsMessage: String) {
        pushToOfflineDevices(userId, wsMessage, emptySet())
    }

    /**
     * Push APNs to devices of [userId] that are NOT currently connected via WebSocket.
     * [onlineAuthTokens] is the set of auth (login) tokens whose WS channels are active.
     * DeviceSessions whose token is in [onlineAuthTokens] are skipped.
     */
    fun pushToOfflineDevices(userId: String, wsMessage: String, onlineAuthTokens: Set<String>) {
        if (apnsClient == null) return

        // 先检查是否有 APNs 设备（缓存 60s，无设备直接跳过避免后续所有查询）
        val apnsCacheKey = "rentmsg:apns:has_token:$userId"
        val hasApns = try { redisTemplate.opsForValue().get(apnsCacheKey) } catch (_: Exception) { null }
        if (hasApns == "0") return

        val allSessions = deviceSessionRepository.findByUserIdAndApnsTokenNotNull(userId)
        try {
            redisTemplate.opsForValue().set(apnsCacheKey, if (allSessions.isEmpty()) "0" else allSessions.size.toString(),
                java.time.Duration.ofSeconds(60))
        } catch (_: Exception) {}

        val offlineSessions = if (onlineAuthTokens.isEmpty()) {
            allSessions
        } else {
            allSessions.filter { it.token !in onlineAuthTokens }
        }

        if (offlineSessions.isEmpty()) return

        val (title, body, data) = parseNotificationContent(wsMessage)
        if (title == null) return

        // 免打扰检查：用 Redis 缓存的 muted 状态
        val convId = data?.get("conversationId") as? String
        if (convId != null) {
            val muteCacheKey = "rentmsg:muted:$userId:$convId"
            val muted = try { redisTemplate.opsForValue().get(muteCacheKey) } catch (_: Exception) { null }
            if (muted == "1") {
                return
            }
            if (muted == null) {
                val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, convId)
                try {
                    redisTemplate.opsForValue().set(muteCacheKey, if (uc?.muted == true) "1" else "0",
                        java.time.Duration.ofSeconds(60))
                } catch (_: Exception) {}
                if (uc?.muted == true) {
                    log.debug("[推送] 会话 {} 已被用户 {} 免打扰，跳过APNs推送", convId, userId)
                    return
                }
            }
        }

        // Bug #18: 使用实际未读总数作为角标，而非硬编码 1
        val badgeCount = try {
            conversationService.getTotalUnreadCount(userId).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }

        for (session in offlineSessions) {
            val token = session.apnsToken ?: continue
            sendPush(token, title, body ?: "", data, badgeCount)
        }
    }

    // ─── VoIP Push ─────────────────────────────────────────────────────────

    /**
     * Send a VoIP Push notification to a single device.
     * Uses the same APNs client but with topic "$bundleId.voip".
     */
    fun sendVoipPush(deviceVoipToken: String, payload: Map<String, Any>) {
        val client = apnsClient ?: return

        val payloadJson = objectMapper.writeValueAsString(payload)
        val sanitizedToken = TokenUtil.sanitizeTokenString(deviceVoipToken)
        val notification = SimpleApnsPushNotification(
            sanitizedToken,
            "$bundleId.voip",
            payloadJson
        )

        val future = client.sendNotification(notification)
        future.whenComplete { response, throwable ->
            if (throwable != null) {
                log.error("[VoIP推送] 发送失败: {}", throwable.message)
                com.layababateam.xinxiwang_backend.extensions.SentryReporter.captureSampled(
                    dedupKey = "im_push_voip_throw:${throwable.javaClass.simpleName}",
                    message = "[IM_PUSH] apns voip failed (throwable)",
                    level = io.sentry.SentryLevel.ERROR,
                    tags = mapOf("im_event" to "apns_voip_fail", "fail_kind" to "throwable"),
                    extras = mapOf(
                        "deviceToken" to com.layababateam.xinxiwang_backend.extensions.SentryReporter.maskToken(deviceVoipToken),
                        "errorCode" to throwable.javaClass.simpleName,
                        "errorMsg" to (throwable.message ?: "")
                    )
                )
            } else if (response != null) {
                if (!response.isAccepted) {
                    val reason = response.rejectionReason.orElse("unknown")
                    com.layababateam.xinxiwang_backend.extensions.SentryReporter.captureSampled(
                        dedupKey = "im_push_voip_rej:$reason",
                        message = "[IM_PUSH] apns voip rejected",
                        level = io.sentry.SentryLevel.ERROR,
                        tags = mapOf("im_event" to "apns_voip_fail", "reason" to reason),
                        extras = mapOf(
                            "deviceToken" to com.layababateam.xinxiwang_backend.extensions.SentryReporter.maskToken(deviceVoipToken),
                            "errorCode" to reason
                        )
                    )
                    if (reason == "BadDeviceToken" || reason == "Unregistered") {
                        try {
                            deviceSessionRepository.findByVoipToken(deviceVoipToken).forEach { session ->
                                deviceSessionRepository.save(session.copy(voipToken = null))
                            }
                        } catch (e: Exception) {
                            log.error("[VoIP推送] 清除无效voipToken失败: {}", e.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Push VoIP notification to offline devices of [userId].
     * [onlineAuthTokens] is the set of auth tokens whose WS channels are active — those are skipped.
     */
    fun pushVoipToOfflineDevices(userId: String, callData: Map<String, Any>, onlineAuthTokens: Set<String>) {
        if (apnsClient == null) return

        val allSessions = deviceSessionRepository.findByUserIdAndVoipTokenNotNull(userId)
        val offlineSessions = if (onlineAuthTokens.isEmpty()) {
            allSessions
        } else {
            allSessions.filter { it.token !in onlineAuthTokens }
        }

        if (offlineSessions.isEmpty()) return

        for (session in offlineSessions) {
            val voipToken = session.voipToken ?: continue
            sendVoipPush(voipToken, callData)
        }
    }

    /**
     * Plan A（微信式）：對所有離線設備發送來電 alert push（取代 VoIP push）。
     * 使用 ringtone.caf 音效 + 完整 callData payload 供冷啟動恢復。
     */
    fun pushIncomingCallAlertToOfflineDevices(
        userId: String,
        callData: Map<String, Any>,
        onlineAuthTokens: Set<String>
    ) {
        if (apnsClient == null) return

        val allSessions = deviceSessionRepository.findByUserIdAndApnsTokenNotNull(userId)
        val offlineSessions = if (onlineAuthTokens.isEmpty()) {
            allSessions
        } else {
            allSessions.filter { it.token !in onlineAuthTokens }
        }

        if (offlineSessions.isEmpty()) {
            log.info("[來電Alert推送] userId={} 無離線 apnsToken session", userId)
            return
        }

        val callerName = callData["callerName"] as? String ?: "来电"
        val callType = (callData["callType"] as? Number)?.toInt() ?: 0
        val title = "來電"
        val body = if (callType == 1) "$callerName 邀請你視訊通話" else "$callerName 邀請你語音通話"

        // expireAt: 60 秒後過期（與 _outgoingTimeoutSec 一致）
        val expireAt = System.currentTimeMillis() + 60_000L

        // 為冷啟動恢復帶完整 callData payload
        val customData = mutableMapOf<String, Any>(
            "type" to "incoming_call",
            "roomId" to (callData["roomId"] ?: 0),
            "callerId" to (callData["callerId"] ?: ""),
            "callerName" to callerName,
            "callerAvatar" to (callData["callerAvatar"] ?: ""),
            "callType" to callType,
            "userSig" to (callData["userSig"] ?: ""),
            "sdkAppId" to (callData["sdkAppId"] ?: 0),
            "expireAt" to expireAt
        )

        log.info("[來電Alert推送] userId={} 發送 {} 設備", userId, offlineSessions.size)
        for (session in offlineSessions) {
            val apnsToken = session.apnsToken ?: continue
            sendPush(
                deviceToken = apnsToken,
                title = title,
                body = body,
                customData = customData,
                badgeCount = 1
            )
        }
    }

    /**
     * Low-level push: send a single APNs notification.
     *
     * Plan A（微信式）：incoming_call 絕不設 content-available。
     * 靜默喚醒會觸發 clearBadge / applicationDidBecomeActive 自撤剛到的來電通知。
     */
    fun sendPush(
        deviceToken: String,
        title: String,
        body: String,
        customData: Map<String, Any>? = null,
        badgeCount: Int = 1,
        collapseId: String? = null
    ) {
        val client = apnsClient ?: return

        val isIncomingCall = customData?.get("type") == "incoming_call"
        val soundName = if (isIncomingCall) "ringtone.caf" else "default"
        // Plan A：incoming_call 不設 mutable-content（避免 NSE 改寫 sound 切斷鈴聲），
        // 不設 content-available（避免靜默喚醒觸發 clearBadge 自撤通知）。
        // 一般訊息推送仍走 mutable-content 由 NSE 下載頭像/圖片附件。
        val payloadBuilder: ApnsPayloadBuilder = SimpleApnsPayloadBuilder()
            .setAlertTitle(title)
            .setAlertBody(body)
            .setSound(soundName)
            .setBadgeNumber(badgeCount)
            .setMutableContent(!isIncomingCall)

        // 來電通知設為 time-sensitive + INCOMING_CALL category（接聽/拒絕 action）
        if (isIncomingCall) {
            payloadBuilder.setCategoryName("INCOMING_CALL")
            payloadBuilder.setInterruptionLevel(InterruptionLevel.TIME_SENSITIVE)
        }

        customData?.forEach { (key, value) ->
            payloadBuilder.addCustomProperty(key, value)
        }

        val payload = payloadBuilder.build()
        val sanitizedToken = TokenUtil.sanitizeTokenString(deviceToken)
        val notification = if (collapseId != null) {
            SimpleApnsPushNotification(
                sanitizedToken, bundleId, payload,
                null, DeliveryPriority.IMMEDIATE, PushType.ALERT, collapseId
            )
        } else {
            SimpleApnsPushNotification(sanitizedToken, bundleId, payload)
        }

        val future = client.sendNotification(notification)
        future.whenComplete { response, throwable ->
            if (throwable != null) {
                log.error("APNs send failed for token {}: {}", deviceToken, throwable.message)
                com.layababateam.xinxiwang_backend.extensions.SentryReporter.captureSampled(
                    dedupKey = "im_push_apns_throw:${throwable.javaClass.simpleName}",
                    message = "[IM_PUSH] apns send failed (throwable)",
                    level = io.sentry.SentryLevel.ERROR,
                    tags = mapOf("im_event" to "apns_fail", "fail_kind" to "throwable"),
                    extras = mapOf(
                        "deviceToken" to com.layababateam.xinxiwang_backend.extensions.SentryReporter.maskToken(deviceToken),
                        "errorCode" to throwable.javaClass.simpleName,
                        "errorMsg" to (throwable.message ?: "")
                    )
                )
            } else if (response != null) {
                if (!response.isAccepted) {
                    val reason = response.rejectionReason.orElse("unknown")
                    com.layababateam.xinxiwang_backend.extensions.SentryReporter.captureSampled(
                        dedupKey = "im_push_apns_rej:$reason",
                        message = "[IM_PUSH] apns send rejected",
                        level = io.sentry.SentryLevel.ERROR,
                        tags = mapOf("im_event" to "apns_fail", "reason" to reason),
                        extras = mapOf(
                            "deviceToken" to com.layababateam.xinxiwang_backend.extensions.SentryReporter.maskToken(deviceToken),
                            "errorCode" to reason
                        )
                    )
                    if (reason == "BadDeviceToken" || reason == "Unregistered") {
                        try {
                            deviceSessionRepository.findByApnsToken(deviceToken).forEach { session ->
                                deviceSessionRepository.save(session.copy(apnsToken = null))
                            }
                        } catch (e: Exception) {
                            log.error("[推送] 清除无效token失败: {}", e.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * 发送聚合后的群消息推送。
     * 使用 collapse-id = conversationId，让同一群聊的推送在设备上只显示一条（替换而非堆叠）。
     */
    fun sendAggregatedGroupPush(
        userId: String,
        title: String,
        body: String,
        customData: Map<String, Any>,
        badgeCount: Int,
        collapseId: String,
        onlineAuthTokens: Set<String> = emptySet()
    ) {
        if (apnsClient == null) return

        val allSessions = deviceSessionRepository.findByUserIdAndApnsTokenNotNull(userId)
        if (allSessions.isEmpty()) return

        val offlineSessions = if (onlineAuthTokens.isEmpty()) {
            allSessions
        } else {
            allSessions.filter { it.token !in onlineAuthTokens }
        }

        if (offlineSessions.isEmpty()) return

        for (session in offlineSessions) {
            val token = session.apnsToken ?: continue
            sendPush(token, title, body, customData, badgeCount, collapseId)
        }
    }

    /**
     * Extract human-readable title/body from the WebSocket JSON payload.
     */
    private fun parseNotificationContent(wsMessage: String): Triple<String?, String?, Map<String, Any>?> {
        return try {
            val json = objectMapper.readValue(wsMessage, Map::class.java)
            val type = json["type"] as? String
            val data = json["data"] as? Map<*, *>

            when (type) {
                "new_message" -> {
                    val senderName = data?.get("senderName") as? String ?: "新消息"
                    val senderAvatar = data?.get("senderAvatar") as? String
                    val groupName = data?.get("groupName") as? String
                    val groupAvatar = data?.get("groupAvatar") as? String
                    val contentType = (data?.get("contentType") as? Number)?.toInt() ?: 0
                    val content = data?.get("content") as? String ?: ""
                    val convId = data?.get("conversationId") as? String
                    val isGroup = !groupName.isNullOrBlank()

                    val customData = mutableMapOf<String, Any>("type" to "new_message")
                    if (convId != null) customData["conversationId"] = convId
                    // 群聊用群頭像，私聊用發送者頭像
                    val avatarUrl = if (isGroup) groupAvatar else senderAvatar
                    if (avatarUrl != null) customData["avatarUrl"] = avatarUrl

                    log.info("[推送调试] ContentType={}, Content={}, isGroup={}", contentType, content, isGroup)

                    val bodyText = when (contentType) {
                        0 -> content.take(100)
                        1 -> {
                            // Try to extract image URL from JSON content if it's a JSON string
                            // Or directly use the content if it's a URL
                            try {
                                val imageJson = objectMapper.readValue(content, Map::class.java)
                                val imageUrl = imageJson["url"] as? String
                                if (imageUrl != null) {
                                    customData["imageUrl"] = imageUrl
                                    log.info("[推送调试] 成功从JSON提取图片URL: {}", imageUrl)
                                }
                            } catch (_: Exception) {
                                // If content is just a raw URL string
                                if (content.startsWith("http")) {
                                    customData["imageUrl"] = content
                                    log.info("[推送调试] 成功识别纯文本图片URL: {}", content)
                                } else {
                                    log.warn("[推送调试] 无法从内容中提取图片URL. Content: {}", content)
                                }
                            }
                            "[图片]"
                        }
                        2 -> "[语音]"
                        3 -> "[视频]"
                        4 -> "[文件]"
                        5 -> "[通话]"
                        6 -> "[系统通知]"
                        7 -> "[红包]"
                        8 -> "[表情]"
                        10 -> "[转账]"
                        11 -> "[红包]"
                        12 -> {
                            // Parse wallet card JSON for detailed content
                            try {
                                val cardJson = objectMapper.readValue(content, Map::class.java)
                                val title = cardJson["title"] as? String ?: "积分变动通知"
                                val amount = cardJson["amount"] as? String
                                if (amount != null) "$title：$amount 积分" else title
                            } catch (_: Exception) { "[积分变动通知]" }
                        }
                        13 -> "[个人名片]"
                        14 -> "[群聊名片]"
                        16 -> "[会议]"
                        else -> "[消息]"
                    }

                    // 群聊：title=群名稱，body="發送人: 消息內容"
                    val title = if (isGroup) groupName!! else senderName
                    val body = if (isGroup) "$senderName: $bodyText" else bodyText

                    Triple(title, body, customData)
                }
                "friend_request_notification" -> {
                    val fromName = data?.get("fromDisplayName") as? String ?: "有人"
                    val fromAvatar = data?.get("fromAvatarUrl") as? String

                    val customData = mutableMapOf<String, Any>("type" to "friend_request")
                    if (fromAvatar != null) customData["avatarUrl"] = fromAvatar

                    Triple("好友请求", "${fromName}请求添加你为好友", customData)
                }
                "incoming_call" -> {
                    val callerName = data?.get("callerName") as? String ?: "有人"
                    val callerAvatar = data?.get("callerAvatar") as? String
                    val callType = (data?.get("callType") as? Number)?.toInt() ?: 0
                    val callTypeStr = if (callType == 1) "视频" else "语音"

                    val customData = mutableMapOf<String, Any>("type" to "incoming_call")
                    if (callerAvatar != null) customData["avatarUrl"] = callerAvatar

                    Triple("来电", "${callerName}邀请你${callTypeStr}通话", customData)
                }
                else -> Triple(null, null, null)
            }
        } catch (e: Exception) {
            log.debug("Failed to parse WS message for push: {}", e.message)
            Triple(null, null, null)
        }
    }
}
