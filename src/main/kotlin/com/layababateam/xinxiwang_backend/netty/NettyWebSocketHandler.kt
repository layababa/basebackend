package com.layababateam.xinxiwang_backend.netty

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.handler.MessageHandler
import com.layababateam.xinxiwang_backend.metrics.WebSocketMetrics
import com.layababateam.xinxiwang_backend.config.MediaKeyRegistry
import com.layababateam.xinxiwang_backend.proto.WsEnvelope
import com.layababateam.xinxiwang_backend.repository.DeviceSessionRepository
import com.layababateam.xinxiwang_backend.service.AckRetryService
import com.layababateam.xinxiwang_backend.service.CallSessionAudit
import com.layababateam.xinxiwang_backend.service.CallSessionManager
import com.layababateam.xinxiwang_backend.service.ClientUpdatePolicyService
import com.layababateam.xinxiwang_backend.service.MeetingService
import com.layababateam.xinxiwang_backend.service.TrtcService
import com.layababateam.xinxiwang_backend.service.PullLogCommandSender
import com.layababateam.xinxiwang_backend.service.UserSessionManager
import io.netty.util.AttributeKey
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Sharable
@Component
class NettyWebSocketHandler(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val userSessionManager: UserSessionManager,
    private val callSessionManager: CallSessionManager,
    private val meetingService: MeetingService,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val clientUpdatePolicyService: ClientUpdatePolicyService,
    private val wsMetrics: WebSocketMetrics,
    private val businessMetrics: com.layababateam.xinxiwang_backend.metrics.BusinessMetrics,
    private val wsResponseHelper: WsResponseHelper,
    private val pullLogCommandSender: PullLogCommandSender,
    private val ackRetryService: AckRetryService,
    private val mediaKeyRegistry: MediaKeyRegistry,
    private val callSessionAudit: CallSessionAudit,
    private val trtcService: TrtcService,
    @Value("\${rentmsg.ws.executor.auth.threads:16}") private val authThreads: Int,
    @Value("\${rentmsg.ws.executor.auth.queue-capacity:512}") private val authQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.send.threads:32}") private val sendThreads: Int,
    @Value("\${rentmsg.ws.executor.send.queue-capacity:2048}") private val sendQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.ack.threads:16}") private val ackThreads: Int,
    @Value("\${rentmsg.ws.executor.ack.queue-capacity:4096}") private val ackQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.light.threads:16}") private val lightThreads: Int,
    @Value("\${rentmsg.ws.executor.light.queue-capacity:2048}") private val lightQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.bootstrap.threads:8}") private val bootstrapThreads: Int,
    @Value("\${rentmsg.ws.executor.bootstrap.queue-capacity:512}") private val bootstrapQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.query.threads:16}") private val queryThreads: Int,
    @Value("\${rentmsg.ws.executor.query.queue-capacity:1024}") private val queryQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.business.threads:32}") private val businessThreads: Int,
    @Value("\${rentmsg.ws.executor.business.queue-capacity:1024}") private val businessQueueCapacity: Int,
    @Value("\${rentmsg.ws.executor.background.core-threads:8}") private val backgroundCoreThreads: Int,
    @Value("\${rentmsg.ws.executor.background.max-threads:16}") private val backgroundMaxThreads: Int,
    @Value("\${rentmsg.ws.executor.background.queue-capacity:2000}") private val backgroundQueueCapacity: Int,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.enabled:true}") private val queryRateLimitEnabled: Boolean,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.max-per-second:12}") private val queryRateLimitMaxPerSecond: Int,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.window-ms:1000}") private val queryRateLimitWindowMs: Long,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.max-queued:128}") private val queryRateLimitMaxQueuedPerIp: Int,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.max-delay-ms:5000}") private val queryRateLimitMaxDelayMs: Long,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.coalesce-enabled:true}") private val queryCoalesceEnabled: Boolean,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.coalesce-window-ms:20}") private val queryCoalesceWindowMs: Long,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.max-coalesced-waiters:64}") private val queryMaxCoalescedWaiters: Int,
    @Value("\${rentmsg.ws.ratelimit.query-per-ip.executor-queue-high-watermark:0.75}") private val queryExecutorQueueHighWatermark: Double,
    handlers: List<MessageHandler>
) : SimpleChannelInboundHandler<WebSocketFrame>() {

    companion object {
        val CLIENT_VERSION_KEY: AttributeKey<String> = AttributeKey.valueOf("sentry.client.version")
        val CLIENT_PLATFORM_KEY: AttributeKey<String> = AttributeKey.valueOf("sentry.client.platform")
        private const val CLIENT_QUERY_BUSY_MESSAGE = "请求繁忙，请稍后重试"
        private val REQUEST_METADATA_KEYS = setOf("requestId", "clientMessageId")
    }

    private val log = LoggerFactory.getLogger(NettyWebSocketHandler::class.java)
    private val handlerMap: Map<String, MessageHandler> = handlers.associateBy { it.type }
    private val backgroundTaskKeys = ConcurrentHashMap.newKeySet<String>()

    private val ackTaskTypes = setOf(
        "message_ack",
        "batch_ack",
        "update_read_point",
        "batch_update_read_points",
        "client_log_config_ack",
    )

    private val lightTaskTypes = setOf(
        "typing_status",
        "presence_query",
        "check_pending_call",
        "get_my_call_state",
        "getUserInfo",
    )

    private val bootstrapTaskTypes = setOf(
        "conversation_list",
        "v3_conversation_list",
        "v3_conversation_sync",
    )

    private val queryTaskTypes = setOf(
        "sync",
        "v3_sync",
        "v3_batch_sync",
        "v3_query_messages",
        "get_history",
        "get_recent_history",
        "batch_get_history",
        "get_group_members",
        "get_conversation_info",
    )

    private val ipRateLimitedQueryTaskTypes = setOf(
        "conversation_list",
        "v3_conversation_list",
        "v3_conversation_sync",
        "sync",
        "v3_sync",
        "v3_batch_sync",
        "v3_query_messages",
        "get_history",
        "get_recent_history",
        "batch_get_history",
        "get_group_members",
        "get_conversation_info",
    )

    private fun positive(value: Int): Int = value.coerceAtLeast(1)

    private fun newWsExecutor(
        name: String,
        coreSize: Int,
        maxSize: Int,
        queueCapacity: Int
    ) = ThreadPoolExecutor(
        coreSize,
        maxSize,
        60L, TimeUnit.SECONDS,
        java.util.concurrent.LinkedBlockingQueue(queueCapacity),
        { r -> Thread(r, "$name-${System.nanoTime()}").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    // Keep latency-sensitive traffic away from sync/history and Rabbit-backed work.
    private val authExecutor = newWsExecutor(
        "ws-auth",
        positive(authThreads),
        positive(authThreads),
        positive(authQueueCapacity),
    )
    private val sendExecutor = newWsExecutor(
        "ws-send",
        positive(sendThreads),
        positive(sendThreads),
        positive(sendQueueCapacity),
    )
    private val ackExecutor = newWsExecutor(
        "ws-ack",
        positive(ackThreads),
        positive(ackThreads),
        positive(ackQueueCapacity),
    )
    private val lightExecutor = newWsExecutor(
        "ws-light",
        positive(lightThreads),
        positive(lightThreads),
        positive(lightQueueCapacity),
    )
    private val bootstrapExecutor = newWsExecutor(
        "ws-bootstrap",
        positive(bootstrapThreads),
        positive(bootstrapThreads),
        positive(bootstrapQueueCapacity),
    )
    private val queryExecutor = newWsExecutor(
        "ws-query",
        positive(queryThreads),
        positive(queryThreads),
        positive(queryQueueCapacity),
    )
    private val businessExecutor = newWsExecutor(
        "ws-biz",
        positive(businessThreads),
        positive(businessThreads),
        positive(businessQueueCapacity),
    )
    private val backgroundExecutor = newWsExecutor(
        "ws-bg",
        positive(backgroundCoreThreads),
        positive(backgroundMaxThreads).coerceAtLeast(positive(backgroundCoreThreads)),
        positive(backgroundQueueCapacity),
    )
    private val querySmoothingScheduler = ScheduledThreadPoolExecutor(2) { r ->
        Thread(r, "ws-query-smooth-${System.nanoTime()}").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
    }
    private val queryIpNextAvailableNanos = ConcurrentHashMap<String, AtomicLong>()
    private val queryIpQueuedCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingCoalescedQueries = ConcurrentHashMap<String, PendingCoalescedQuery>()

    private class PendingCoalescedQuery {
        private val waiters = mutableListOf<WsResponseHelper.CoalescedResponseTarget>()
        private var started = false

        fun tryAdd(target: WsResponseHelper.CoalescedResponseTarget, maxWaiters: Int): Boolean = synchronized(this) {
            if (started || waiters.size >= maxWaiters) {
                false
            } else {
                waiters += target
                true
            }
        }

        fun start(): List<WsResponseHelper.CoalescedResponseTarget> = synchronized(this) {
            started = true
            waiters.toList()
        }
    }

    private fun executorFor(type: String): Pair<String, ThreadPoolExecutor> =
        when (type) {
            "auth" -> "auth" to authExecutor
            in ackTaskTypes -> "ack" to ackExecutor
            in lightTaskTypes -> "light" to lightExecutor
            in bootstrapTaskTypes -> "bootstrap" to bootstrapExecutor
            in queryTaskTypes -> "query" to queryExecutor
            "chat_message",
            "message_forward",
            "message_forward_batch",
            "wallet_transfer",
            "wallet_send_red_packet" -> "send" to sendExecutor
            else -> "business" to businessExecutor
        }

    init {
        wsMetrics.bindExecutor("auth", authExecutor)
        wsMetrics.bindExecutor("send", sendExecutor)
        wsMetrics.bindExecutor("ack", ackExecutor)
        wsMetrics.bindExecutor("light", lightExecutor)
        wsMetrics.bindExecutor("bootstrap", bootstrapExecutor)
        wsMetrics.bindExecutor("query", queryExecutor)
        wsMetrics.bindExecutor("business", businessExecutor)
        wsMetrics.bindExecutor("background", backgroundExecutor)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        log.debug("Client connected: {}", ctx.channel().remoteAddress())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val userId = userSessionManager.getUserId(ctx.channel())
        // Unregister FIRST so isOnline() reflects the current state before async cleanup
        userSessionManager.unregister(ctx.channel())
        log.debug("Client disconnected: {}", ctx.channel().remoteAddress())

        if (userId != null) {
            submitBackgroundTask("channelInactive:$userId") {
                try {
                    if (!userSessionManager.isOnlineGlobally(userId)) {
                        val session = callSessionManager.findSessionByUser(userId)
                        if (session != null) {
                            if (session.answeredAt != null) {
                                // 已接通的通话：查 TRTC 房间状态，用户还在房间就不结束
                                if (trtcService.isUserInRoom(session.roomId, userId)) {
                                    log.info("[CALL-DISCONNECT] User {} still in TRTC room {}, keeping call session", userId, session.roomId)
                                } else {
                                    log.info("[CALL-DISCONNECT] User {} not in TRTC room, ending call roomId={}", userId, session.roomId)
                                    val otherUserId = if (session.callerId == userId) session.calleeId else session.callerId
                                    val message = objectMapper.writeValueAsString(mapOf(
                                        "type" to "call_ended",
                                        "data" to mapOf("roomId" to session.roomId, "userId" to userId)
                                    ))
                                    userSessionManager.pushToUser(otherUserId, message)
                                    callSessionAudit.log("ws_disconnect_end_session", session.roomId, userId, mapOf(
                                        "peerId" to otherUserId,
                                        "answered" to true,
                                    ))
                                    callSessionManager.endSessionByUser(userId, null, "disconnected")
                                }
                            }
                        }

                        // 清理会议参与者：用户完全离线后，从所有活跃会议中移除
                        try {
                            meetingService.removeUserFromAllActiveMeetings(userId)
                        } catch (e: Exception) {
                            log.warn("Failed to clean up meetings for user {}: {}", userId, e.message)
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Failed to clean up call session for user {}: {}", userId, e.message)
                }
            }
        }
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        if (ctx.channel().isWritable) {
            userSessionManager.flushPendingMessages(ctx.channel())
        }
        ctx.fireChannelWritabilityChanged()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, frame: WebSocketFrame) {
        val inBytes = frame.content().readableBytes().toLong()
        when (frame) {
            is TextWebSocketFrame -> {
                val text = frame.text()
                val isHeartbeat = text.length < 60 && (text.contains("\"ping\"") || text.contains("\"pong\""))
                businessMetrics.recordWsBytes("in", if (isHeartbeat) "heartbeat" else "business", inBytes)
                handleTextFrame(ctx, frame)
            }
            is BinaryWebSocketFrame -> {
                businessMetrics.recordWsBytes("in", "business", inBytes)
                handleBinaryFrame(ctx, frame)
            }
            is io.netty.handler.codec.http.websocketx.PingWebSocketFrame,
            is io.netty.handler.codec.http.websocketx.PongWebSocketFrame -> {
                businessMetrics.recordWsBytes("in", "heartbeat", inBytes)
            }
            else -> log.debug("Unsupported frame type: {}", frame.javaClass.name)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleTextFrame(ctx: ChannelHandlerContext, frame: TextWebSocketFrame) {
        val message = frame.text()
        val sanitizedLog = if (message.length > 500) message.take(500) + "...[truncated]" else message
        log.debug(
            "Received text from {}: {}",
            ctx.channel().remoteAddress(),
            sanitizedLog.replace(Regex("\"(token|password|paymentPassword)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"***\"")
        )

        try {
            val jsonNode = objectMapper.readTree(message)
            val type = jsonNode.get("type")?.asText() ?: return

            when (type) {
                "auth" -> submitMessageTask(ctx, "auth", null, null) { handleAuth(ctx, jsonNode) }
                "ping" -> {
                    val reqId = jsonNode.get("requestId")?.asText()
                    val pong = if (reqId != null) mapOf("type" to "pong", "requestId" to reqId) else mapOf("type" to "pong")
                    wsResponseHelper.send(ctx, pong)
                }
                else -> routeToHandler(ctx, type, message)
            }
        } catch (e: Exception) {
            log.warn("Failed to process text WS message: {}", message, e)
            captureWsException(e, ctx, "text_frame_error")
            wsResponseHelper.send(ctx, mapOf("type" to "error", "message" to "系统内部错误"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleBinaryFrame(ctx: ChannelHandlerContext, frame: BinaryWebSocketFrame) {
        val bytes = ByteArray(frame.content().readableBytes())
        frame.content().readBytes(bytes)

        try {
            val envelope = WsEnvelope.parseFrom(bytes)
            val type = envelope.type

            log.debug("Received binary from {}: type={}", ctx.channel().remoteAddress(), type)

            when (type) {
                "ping" -> wsResponseHelper.send(ctx, mapOf("type" to "pong"))
                else -> {
                    val userId = userSessionManager.getUserId(ctx.channel())
                    if (userId == null) {
                        wsResponseHelper.send(ctx, mapOf("type" to "error", "message" to "未认证，请重新登录"))
                        return
                    }

                    val handler = handlerMap[type]
                    if (handler == null) {
                        wsResponseHelper.send(ctx, mapOf("type" to "echo", "message" to "unknown type: $type"))
                        return
                    }

                    // Phase 1: extract data from jsonFallback field
                    val jsonFallback = envelope.jsonFallback
                    val data: Map<String, Any?> = if (jsonFallback.isNotEmpty()) {
                        objectMapper.readValue(jsonFallback, Map::class.java) as Map<String, Any?>
                    } else {
                        mapOf("type" to type)
                    }

                    val binaryRequestId = envelope.requestId.takeIf { it.isNotEmpty() }
                        ?: (data["requestId"] as? String)
                    val clientMessageId = data["clientMessageId"] as? String
                    val conversationId = data["conversationId"] as? String
                    wsMetrics.messageReceived(type)
                    submitRoutedMessageTask(
                        ctx = ctx,
                        userId = userId,
                        type = type,
                        handler = handler,
                        data = data,
                        requestId = binaryRequestId,
                        clientMessageId = clientMessageId,
                        conversationId = conversationId,
                    )
                }
            }
        } catch (e: Exception) {
            // Decode failed: attempt JSON fallback
            log.warn("Binary frame decode failed, attempting JSON fallback: {}", e.message)
            try {
                val text = String(bytes, Charsets.UTF_8)
                val fallbackFrame = TextWebSocketFrame(text)
                try {
                    handleTextFrame(ctx, fallbackFrame)
                } finally {
                    if (fallbackFrame.refCnt() > 0) fallbackFrame.release()
                }
            } catch (fallbackEx: Exception) {
                log.warn("JSON fallback also failed: {}", fallbackEx.message)
                wsResponseHelper.send(ctx, mapOf("type" to "error", "message" to "无法解析消息"))
            }
        }
    }

    private fun handleAuth(ctx: ChannelHandlerContext, jsonNode: com.fasterxml.jackson.databind.JsonNode) {
        val token = jsonNode.get("token")?.asText() ?: return
        val userId = redisTemplate.opsForValue().get("rentmsg:tokens:$token")
        if (userId != null) {
            val capabilities = buildSet {
                if (jsonNode.get("supportsBatch")?.asBoolean(false) == true) add("batch")
                if (jsonNode.get("supportsClientLogConfig")?.asBoolean(false) == true) add("client_log_config")
            }
            val requestedProtocol = jsonNode.get("protocol")?.asText()
            val negotiatedProtocol = if (requestedProtocol == "protobuf") ProtocolType.PROTOBUF else ProtocolType.JSON

            // ── Check force-update rule BEFORE granting auth ──────────────────
            val platform = jsonNode.get("platform")?.asText()
            val clientVersion = jsonNode.get("clientVersion")?.asText()
            val osVersion = jsonNode.get("osVersion")?.asText()
            // 热更标签：随 Shorebird patch 下发的字符串，admin 用于区分设备本次热更版本
            val hotpatchTag = jsonNode.get("hotpatchTag")?.asText()
            val session = deviceSessionRepository.findByToken(token)
            val effectivePlatform = platform ?: session?.platform ?: "unknown"
            val effectiveVersion = clientVersion ?: session?.clientVersion ?: "unknown"
            if (effectivePlatform != "unknown" && effectiveVersion != "unknown") {
                val decision = clientUpdatePolicyService.forceDecision(effectivePlatform, effectiveVersion)
                if (decision != null) {
                    sendJson(ctx, clientUpdatePolicyService.forceUpdatePayload(decision))
                    ctx.channel().close()
                    log.info("Force-update: rejected auth for user {} on {} v{} (min={})", userId, effectivePlatform, effectiveVersion, decision.minVersion)
                    return
                }
            }

            // 透传 hotpatchTag 到 UserSessionManager，存入 in-memory session
            userSessionManager.register(userId, ctx.channel(), token, capabilities, negotiatedProtocol, effectivePlatform, effectiveVersion, session?.deviceId, hotpatchTag)
            ctx.channel().attr(CLIENT_VERSION_KEY).set(effectiveVersion)
            ctx.channel().attr(CLIENT_PLATFORM_KEY).set(effectivePlatform)
            val apiVersion = ctx.channel().attr(WebSocketPathRouter.API_VERSION_KEY)?.get() ?: 1
            // Auth response is always JSON (protocol not yet active for this channel)
            sendJson(ctx, mapOf(
                "type" to "auth_success",
                "userId" to userId,
                "negotiatedProtocol" to negotiatedProtocol.name.lowercase(),
                "nodeId" to userSessionManager.nodeId,
                "apiVersion" to apiVersion
            ))
            // 推送当前媒体加密 master key 给新认证的连接，避免客户端首次加密等待 HTTP 拉取超时
            try {
                sendJson(ctx, mapOf(
                    "type" to "media_keys_push",
                    "data" to mediaKeyRegistry.snapshotForClient()
                ))
            } catch (e: Exception) {
                log.warn("media_keys_push send failed: {}", e.message)
            }
            wsMetrics.authSuccess()
            log.info(
                "Channel {} authenticated for user {} protocol={}",
                ctx.channel().id().asShortText(), userId, negotiatedProtocol
            )

            submitBackgroundTask("postAuth:$userId") {
                try {
                    val deviceName = jsonNode.get("deviceName")?.asText()
                    if (session != null) {
                        deviceSessionRepository.save(session.copy(
                            lastActiveAt = System.currentTimeMillis(),
                            deviceName = deviceName ?: session.deviceName,
                            platform = platform ?: session.platform,
                            clientVersion = clientVersion ?: session.clientVersion,
                            osVersion = osVersion ?: session.osVersion
                        ))
                    }

                    val notifyPayload = objectMapper.writeValueAsString(mapOf(
                        "type" to "new_device_login",
                        "data" to mapOf(
                            "deviceName" to (deviceName ?: session?.deviceName ?: "Unknown"),
                            "platform" to (platform ?: session?.platform ?: "unknown"),
                            "ip" to session?.ip
                        )
                    ))
                    userSessionManager.pushToUserExcluding(userId, notifyPayload, ctx.channel())

                    // Replay pending (unACKed) messages to this user.
                    // Uses pushPendingSafely internally to dedup with scanPendingAcks.
                    try {
                        ackRetryService.replayPendingToUser(userId)
                    } catch (e: Exception) {
                        log.warn("Failed to replay pending messages for user {}: {}", userId, e.message)
                    }

                    // Deliver any pending incoming call — peek (non-destructive) so client
                    // can re-fetch via check_pending_call if WS message delivery race-loses.
                    // 真正的 clear 在 call_accept/reject/cancel handler 觸發。
                    try {
                        val pendingCall = callSessionManager.peekPendingCall(userId)
                        if (pendingCall != null) {
                            val pendingData = objectMapper.readTree(pendingCall)
                            val roomId = pendingData.get("data")?.get("roomId")?.asInt()
                            if (roomId != null && callSessionManager.getSession(roomId) != null) {
                                wsResponseHelper.sendRawJson(ctx.channel(), pendingCall)
                                log.info("Delivered pending call to user {}, roomId={}", userId, roomId)
                            } else {
                                // session 已結束才主動清除殘留的 pending call
                                callSessionManager.clearPendingCall(userId)
                                log.info("Pending call for user {} discarded (session no longer active)", userId)
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to deliver pending call for user {}: {}", userId, e.message)
                    }

                    // Deliver any cancel marker left while the callee was cold-booting
                    // (VoIP push showed CallKit, but caller cancelled before WS connected)
                    try {
                        val cancelMarker = callSessionManager.popCancelMarker(userId)
                        if (cancelMarker != null) {
                            wsResponseHelper.sendRawJson(ctx.channel(), cancelMarker)
                            log.info("Delivered cancel marker to user {}", userId)
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to deliver cancel marker for user {}: {}", userId, e.message)
                    }

                    // Push ended meeting IDs so client can update stale meeting bubbles
                    try {
                        val endedIds = meetingService.getEndedMeetingIds(userId)
                        if (endedIds.isNotEmpty()) {
                            val syncPayload = objectMapper.writeValueAsString(mapOf(
                                "type" to "ended_meetings_sync",
                                "data" to mapOf("meetingIds" to endedIds)
                            ))
                            wsResponseHelper.sendRawJson(ctx.channel(), syncPayload)
                            log.info("Pushed ended_meetings_sync to user {}, count={}", userId, endedIds.size)
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to push ended_meetings_sync for user {}: {}", userId, e.message)
                    }

                    // Phase 2 §G3：drain 离线 pull_log_cmd（如果该 device 在离线期间有 admin 触发拉取）
                    // 即使 session 或 deviceId 为 null 也要尝试 drain（V2 key 按 userId 存储，不依赖 deviceId）
                    try {
                        pullLogCommandSender.drainPending(userId, session?.deviceId) { payload ->
                            wsResponseHelper.sendRawJson(ctx.channel(), payload)
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to drain pending pull_log_cmd for user {}: {}", userId, e.message)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to update device session / notify: {}", e.message)
                }
            }
        } else {
            wsMetrics.authFailure()
            sendJson(ctx, mapOf("type" to "auth_failed", "message" to "无效的登录凭证"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun routeToHandler(ctx: ChannelHandlerContext, type: String, message: String) {
        val userId = userSessionManager.getUserId(ctx.channel())
        if (userId == null) {
            wsResponseHelper.send(ctx, mapOf("type" to "error", "message" to "未认证，请重新登录"))
            return
        }

        val handler = handlerMap[type]
        if (handler == null) {
            wsResponseHelper.send(ctx, mapOf("type" to "echo", "message" to message))
            return
        }

        val data = objectMapper.readValue(message, Map::class.java) as Map<String, Any?>
        val requestId = data["requestId"] as? String
        val clientMessageId = data["clientMessageId"] as? String
        val conversationId = data["conversationId"] as? String
        wsMetrics.messageReceived(type)
        submitRoutedMessageTask(
            ctx = ctx,
            userId = userId,
            type = type,
            handler = handler,
            data = data,
            requestId = requestId,
            clientMessageId = clientMessageId,
            conversationId = conversationId,
        )
    }

    private fun submitRoutedMessageTask(
        ctx: ChannelHandlerContext,
        userId: String,
        type: String,
        handler: MessageHandler,
        data: Map<String, Any?>,
        requestId: String?,
        clientMessageId: String?,
        conversationId: String?,
    ) {
        val task: (List<WsResponseHelper.CoalescedResponseTarget>) -> Unit = { coalescedTargets ->
            runHandlerTask(
                ctx = ctx,
                userId = userId,
                type = type,
                handler = handler,
                data = data,
                requestId = requestId,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                coalescedTargets = coalescedTargets,
            )
        }

        if (type in ipRateLimitedQueryTaskTypes && queryRateLimitEnabled) {
            submitSmoothedQueryTask(
                ctx = ctx,
                userId = userId,
                type = type,
                data = data,
                requestId = requestId,
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                task = task,
            )
        } else {
            val fallbackData = if (type in ipRateLimitedQueryTaskTypes) data else emptyMap()
            submitMessageTask(ctx, type, clientMessageId, conversationId, fallbackData) {
                task(emptyList())
            }
        }
    }

    private fun runHandlerTask(
        ctx: ChannelHandlerContext,
        userId: String,
        type: String,
        handler: MessageHandler,
        data: Map<String, Any?>,
        requestId: String?,
        clientMessageId: String?,
        conversationId: String?,
        coalescedTargets: List<WsResponseHelper.CoalescedResponseTarget>,
    ) {
        val startNanos = System.nanoTime()
        try {
            if (coalescedTargets.isNotEmpty()) {
                WsResponseHelper.CURRENT_COALESCED_TARGETS.set(coalescedTargets)
            }
            WsResponseHelper.CURRENT_REQUEST_ID.set(requestId)
            handler.handle(ctx, userId, data)
        } catch (e: IllegalArgumentException) {
            // 业务校验失败（如撤回超时、无权限、红包/通话不可撤回、参数缺失）属于客户端预期错误，
            // 仅记 INFO 并回传错误给客户端，不上报 GlitchTip，避免污染线上告警。
            log.info("Handler validation error for type '{}': {}", type, e.message)
            wsResponseHelper.send(ctx, errorPayload(e.message ?: "操作失败", type, clientMessageId, conversationId))
        } catch (e: Exception) {
            log.warn("Handler error for type '{}': {}", type, e.message, e)
            captureWsException(e, ctx, "handler_error", type)
            wsResponseHelper.send(ctx, errorPayload(e.message ?: "操作失败", type, clientMessageId, conversationId))
        } finally {
            WsResponseHelper.CURRENT_REQUEST_ID.remove()
            WsResponseHelper.CURRENT_COALESCED_TARGETS.remove()
            wsMetrics.recordHandlerDuration(type, System.nanoTime() - startNanos)
        }
    }

    private fun submitSmoothedQueryTask(
        ctx: ChannelHandlerContext,
        userId: String,
        type: String,
        data: Map<String, Any?>,
        requestId: String?,
        clientMessageId: String?,
        conversationId: String?,
        task: (List<WsResponseHelper.CoalescedResponseTarget>) -> Unit,
    ) {
        val coalesceKey = buildQueryCoalesceKey(userId, type, data)
        if (tryJoinPendingQuery(coalesceKey, ctx, requestId, type)) {
            return
        }

        val coalesceMapKey = if (queryCoalesceEnabled) coalesceKey else null
        val pendingQuery = if (coalesceMapKey != null) PendingCoalescedQuery() else null
        coalesceMapKey?.let { key ->
            val query = pendingQuery ?: return@let
            val existing = pendingCoalescedQueries.putIfAbsent(key, query)
            if (existing != null) {
                if (tryAddToPending(existing, ctx, requestId, type)) {
                    return
                }
            }
        }

        val ip = clientIp(ctx)
        val reservedDelayNanos = reserveQuerySlot(ip, type)
        if (reservedDelayNanos == null) {
            val coalescedTargets = startPendingQuery(coalesceMapKey, pendingQuery)
            sendQueryRateLimitedFallback(ctx, type, data, conversationId)
            sendQueryRateLimitedFallbackToTargets(type, data, conversationId, coalescedTargets)
            return
        }

        val coalesceWindowNanos = if (pendingQuery != null) {
            TimeUnit.MILLISECONDS.toNanos(queryCoalesceWindowMs.coerceAtLeast(0L))
        } else {
            0L
        }
        val delayNanos = reservedDelayNanos.coerceAtLeast(coalesceWindowNanos)
        val scheduledTask = Runnable {
            val coalescedTargets = startPendingQuery(coalesceMapKey, pendingQuery)
            val accepted = submitMessageTask(ctx, type, clientMessageId, conversationId, data) {
                try {
                    task(coalescedTargets)
                } finally {
                    releaseQuerySlot(ip)
                }
            }
            if (!accepted) {
                releaseQuerySlot(ip)
                sendQueryRateLimitedFallbackToTargets(type, data, conversationId, coalescedTargets)
            }
        }

        try {
            querySmoothingScheduler.schedule(scheduledTask, delayNanos, TimeUnit.NANOSECONDS)
        } catch (_: RejectedExecutionException) {
            releaseQuerySlot(ip)
            val coalescedTargets = startPendingQuery(coalesceMapKey, pendingQuery)
            wsMetrics.requestRateLimited(type)
            log.info(
                "server_busy suppressed: rejected smoothed WS query '{}' for IP {} because scheduler is saturated; channel={}",
                type,
                ip,
                ctx.channel().id().asShortText()
            )
            sendQueryRateLimitedFallback(ctx, type, data, conversationId)
            sendQueryRateLimitedFallbackToTargets(type, data, conversationId, coalescedTargets)
        }
    }

    private fun tryJoinPendingQuery(
        coalesceKey: String?,
        ctx: ChannelHandlerContext,
        requestId: String?,
        type: String,
    ): Boolean {
        if (!queryCoalesceEnabled || coalesceKey == null) return false
        val pending = pendingCoalescedQueries[coalesceKey] ?: return false
        return tryAddToPending(pending, ctx, requestId, type)
    }

    private fun tryAddToPending(
        pending: PendingCoalescedQuery,
        ctx: ChannelHandlerContext,
        requestId: String?,
        type: String,
    ): Boolean {
        val added = pending.tryAdd(
            WsResponseHelper.CoalescedResponseTarget(ctx, requestId),
            queryMaxCoalescedWaiters.coerceAtLeast(0),
        )
        if (added) {
            log.debug(
                "Coalesced duplicate WS query '{}' for channel {}",
                type,
                ctx.channel().id().asShortText()
            )
        }
        return added
    }

    private fun startPendingQuery(
        coalesceKey: String?,
        pending: PendingCoalescedQuery?,
    ): List<WsResponseHelper.CoalescedResponseTarget> {
        if (coalesceKey == null || pending == null) return emptyList()
        pendingCoalescedQueries.remove(coalesceKey, pending)
        return pending.start()
    }

    private fun reserveQuerySlot(ip: String, type: String): Long? {
        val queuedCounter = queryIpQueuedCounts.computeIfAbsent(ip) { AtomicInteger(0) }
        val queued = queuedCounter.incrementAndGet()
        val maxQueued = queryRateLimitMaxQueuedPerIp.coerceAtLeast(1)
        if (queued > maxQueued) {
            queuedCounter.decrementAndGet()
            wsMetrics.requestRateLimited(type)
            log.info(
                "server_busy suppressed: rejected WS query '{}' for IP {} because smoothing queue is full: queued={} maxQueued={}",
                type,
                ip,
                queued,
                maxQueued
            )
            return null
        }

        val now = System.nanoTime()
        val intervalNanos = (
            TimeUnit.MILLISECONDS.toNanos(queryRateLimitWindowMs.coerceAtLeast(100L)) /
                queryRateLimitMaxPerSecond.coerceAtLeast(1)
            ).coerceAtLeast(TimeUnit.MILLISECONDS.toNanos(1L))
        val maxDelayNanos = TimeUnit.MILLISECONDS.toNanos(queryRateLimitMaxDelayMs.coerceAtLeast(0L))
        val nextAvailable = queryIpNextAvailableNanos.computeIfAbsent(ip) { AtomicLong(0L) }

        while (true) {
            val currentNext = nextAvailable.get()
            val scheduledAt = currentNext.coerceAtLeast(now)
            val delayNanos = scheduledAt - now
            if (delayNanos > maxDelayNanos) {
                releaseQuerySlot(ip)
                wsMetrics.requestRateLimited(type)
                log.info(
                    "server_busy suppressed: rejected WS query '{}' for IP {} because smoothing delay is too high: delayMs={} maxDelayMs={}",
                    type,
                    ip,
                    TimeUnit.NANOSECONDS.toMillis(delayNanos),
                    queryRateLimitMaxDelayMs.coerceAtLeast(0L)
                )
                return null
            }

            val newNext = scheduledAt + intervalNanos
            if (nextAvailable.compareAndSet(currentNext, newNext)) {
                return delayNanos
            }
        }
    }

    private fun releaseQuerySlot(ip: String) {
        val queuedCounter = queryIpQueuedCounts[ip] ?: return
        val queued = queuedCounter.decrementAndGet()
        if (queued <= 0) {
            queryIpQueuedCounts.remove(ip, queuedCounter)
            val nextAvailable = queryIpNextAvailableNanos[ip]
            if (nextAvailable != null && nextAvailable.get() <= System.nanoTime()) {
                queryIpNextAvailableNanos.remove(ip, nextAvailable)
            }
        }
    }

    private fun buildQueryCoalesceKey(userId: String, type: String, data: Map<String, Any?>): String? {
        if (!queryCoalesceEnabled) return null
        return try {
            val normalized = TreeMap<String, Any?>()
            data.forEach { (key, value) ->
                if (key !in REQUEST_METADATA_KEYS) {
                    normalized[key] = value
                }
            }
            "$type:$userId:${objectMapper.writeValueAsString(normalized)}"
        } catch (e: Exception) {
            log.debug("Failed to build WS query coalesce key for type '{}': {}", type, e.message)
            null
        }
    }

    private fun clientIp(ctx: ChannelHandlerContext): String =
        ctx.channel().attr(WebSocketPathRouter.CLIENT_IP_KEY).get()
            ?: (ctx.channel().remoteAddress() as? InetSocketAddress)?.address?.hostAddress
            ?: "unknown"

    private fun sendQueryRateLimitedFallback(
        ctx: ChannelHandlerContext,
        type: String,
        data: Map<String, Any?>,
        conversationId: String?,
    ) {
        wsResponseHelper.send(ctx, queryRateLimitedFallbackPayload(type, data, conversationId))
    }

    private fun sendQueryRateLimitedFallbackToTargets(
        type: String,
        data: Map<String, Any?>,
        conversationId: String?,
        targets: List<WsResponseHelper.CoalescedResponseTarget>,
    ) {
        if (targets.isEmpty()) return
        val payload = queryRateLimitedFallbackPayload(type, data, conversationId)
        targets.forEach { target ->
            wsResponseHelper.sendWithRequestId(target.ctx, payload, target.requestId)
        }
    }

    private fun queryRateLimitedFallbackPayload(
        type: String,
        data: Map<String, Any?>,
        conversationId: String?,
    ): Map<String, Any?> {
        val convId = conversationId ?: ""
        return when (type) {
            "sync", "v3_sync" -> mapOf(
                "type" to "sync_response",
                "conversationId" to convId,
                "data" to emptyList<Any>(),
                "hasMore" to false,
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "conversation_list" -> mapOf(
                "type" to "conversation_list_response",
                "data" to emptyList<Any>(),
                "hasMore" to false,
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "v3_conversation_list" -> mapOf(
                "type" to "v3_conversation_list_response",
                "data" to emptyList<Any>(),
                "hasMore" to false,
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "v3_conversation_sync" -> mapOf(
                "type" to "v3_conversation_sync_response",
                "data" to emptyList<Any>(),
                "syncTimestamp" to System.currentTimeMillis(),
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "v3_query_messages" -> mapOf(
                "type" to "v3_query_messages_response",
                "conversationId" to convId,
                "data" to emptyList<Any>(),
                "hasMore" to false,
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "v3_batch_sync" -> {
                val results = (data["conversations"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { req ->
                        mapOf(
                            "conversationId" to ((req["conversationId"] as? String) ?: ""),
                            "data" to emptyList<Any>(),
                            "hasMore" to false,
                            "rateLimited" to true,
                            "message" to CLIENT_QUERY_BUSY_MESSAGE,
                        )
                    }
                    ?: emptyList()
                mapOf(
                    "type" to "v3_batch_sync_response",
                    "data" to results,
                    "rateLimited" to true,
                    "message" to CLIENT_QUERY_BUSY_MESSAGE,
                )
            }
            "get_history" -> mapOf(
                "type" to "history_response",
                "conversationId" to convId,
                "data" to emptyList<Any>(),
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "get_recent_history" -> mapOf(
                "type" to "recent_history_response",
                "conversationId" to convId,
                "data" to emptyList<Any>(),
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "batch_get_history" -> mapOf(
                "type" to "batch_history_response",
                "data" to emptyMap<String, List<Any>>(),
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "get_group_members" -> mapOf(
                "type" to "group_members_response",
                "conversationId" to convId,
                "data" to emptyList<Any>(),
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            "get_conversation_info" -> mapOf(
                "type" to "conversation_info_response",
                "data" to null,
                "rateLimited" to true,
                "message" to CLIENT_QUERY_BUSY_MESSAGE,
            )
            else -> errorPayload(CLIENT_QUERY_BUSY_MESSAGE, type, data["clientMessageId"] as? String, conversationId)
        }
    }

    private fun submitMessageTask(
        ctx: ChannelHandlerContext,
        type: String,
        clientMessageId: String?,
        conversationId: String?,
        rateLimitedFallbackData: Map<String, Any?> = emptyMap(),
        task: () -> Unit
    ): Boolean {
        val (executorName, executor) = executorFor(type)
        val submittedAt = System.nanoTime()
        if (shouldShedRateLimitedTask(type, executor)) {
            wsMetrics.requestRateLimited(type)
            log.info(
                "server_busy suppressed: shed WS task '{}' for channel {} because {} executor queue is above high watermark: active={} queue={} remaining={}",
                type,
                ctx.channel().id().asShortText(),
                executorName,
                executor.activeCount,
                executor.queue.size,
                executor.queue.remainingCapacity()
            )
            sendQueryRateLimitedFallback(ctx, type, rateLimitedFallbackData, conversationId)
            return false
        }
        try {
            executor.execute {
                wsMetrics.recordTaskQueueDelay(executorName, type, System.nanoTime() - submittedAt)
                task()
            }
            return true
        } catch (_: RejectedExecutionException) {
            log.info(
                "server_busy suppressed: rejected WS task '{}' for channel {} because {} executor is saturated: active={} queue={} completed={}",
                type,
                ctx.channel().id().asShortText(),
                executorName,
                executor.activeCount,
                executor.queue.size,
                executor.completedTaskCount
            )
            if (type in ipRateLimitedQueryTaskTypes) {
                wsMetrics.requestRateLimited(type)
                sendQueryRateLimitedFallback(ctx, type, rateLimitedFallbackData, conversationId)
            } else {
                wsResponseHelper.send(ctx, errorPayload("请求繁忙，请稍后再试", type, clientMessageId, conversationId))
            }
            return false
        }
    }

    private fun shouldShedRateLimitedTask(type: String, executor: ThreadPoolExecutor): Boolean {
        if (type !in ipRateLimitedQueryTaskTypes) return false
        val queue = executor.queue
        val capacity = queue.size + queue.remainingCapacity()
        if (capacity <= 0) return false
        val highWatermark = queryExecutorQueueHighWatermark.coerceIn(0.10, 0.99)
        return queue.size.toDouble() / capacity.toDouble() >= highWatermark
    }

    private fun errorPayload(
        message: String,
        requestType: String,
        clientMessageId: String?,
        conversationId: String?,
    ): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "type" to "error",
            "message" to message,
            "requestType" to requestType,
        )
        if (!clientMessageId.isNullOrBlank()) {
            payload["clientMessageId"] = clientMessageId
        }
        if (!conversationId.isNullOrBlank()) {
            payload["conversationId"] = conversationId
        }
        return payload
    }

    private fun submitBackgroundTask(taskName: String, task: () -> Unit) {
        val dedupKey = backgroundTaskDedupKey(taskName)
        if (dedupKey != null && !backgroundTaskKeys.add(dedupKey)) {
            log.debug("Dropped duplicate background task '{}'", taskName)
            return
        }
        val wrappedTask = Runnable {
            try {
                task()
            } finally {
                if (dedupKey != null) backgroundTaskKeys.remove(dedupKey)
            }
        }
        try {
            backgroundExecutor.execute(wrappedTask)
        } catch (_: RejectedExecutionException) {
            if (dedupKey != null) backgroundTaskKeys.remove(dedupKey)
            log.warn("Dropped background task '{}' because background executor is saturated", taskName)
        }
    }

    private fun backgroundTaskDedupKey(taskName: String): String? =
        when {
            taskName.startsWith("postAuth:") -> taskName
            taskName.startsWith("channelInactive:") -> taskName
            else -> null
        }

    @PreDestroy
    fun shutdownExecutors() {
        authExecutor.shutdown()
        sendExecutor.shutdown()
        ackExecutor.shutdown()
        lightExecutor.shutdown()
        bootstrapExecutor.shutdown()
        queryExecutor.shutdown()
        querySmoothingScheduler.shutdown()
        businessExecutor.shutdown()
        backgroundExecutor.shutdown()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.error("Channel error: {}", ctx.channel().remoteAddress(), cause)
        val userId = userSessionManager.getUserId(ctx.channel())
        Sentry.withScope { scope ->
            scope.setTag("layer", "netty-websocket")
            scope.setTag("channel", ctx.channel().id().asShortText())
            scope.level = SentryLevel.ERROR
            userId?.let { scope.setTag("userId", it) }
            ctx.channel().attr(CLIENT_VERSION_KEY)?.get()?.let { scope.setTag("client.version", it) }
            ctx.channel().attr(CLIENT_PLATFORM_KEY)?.get()?.let { scope.setTag("client.platform", it) }
            scope.setContexts("websocket", mapOf(
                "remoteAddress" to ctx.channel().remoteAddress().toString(),
                "channelId" to ctx.channel().id().asShortText(),
                "userId" to (userId ?: "unauthenticated")
            ))
            Sentry.captureException(cause)
        }
        ctx.close()
    }

    private fun captureWsException(
        e: Throwable,
        ctx: ChannelHandlerContext,
        tag: String,
        messageType: String? = null
    ) {
        val userId = userSessionManager.getUserId(ctx.channel())
        Sentry.withScope { scope ->
            scope.setTag("layer", "netty-websocket")
            scope.setTag("ws.error", tag)
            scope.level = SentryLevel.ERROR
            messageType?.let { scope.setTag("ws.messageType", it) }
            userId?.let { scope.setTag("userId", it) }
            ctx.channel().attr(CLIENT_VERSION_KEY)?.get()?.let { scope.setTag("client.version", it) }
            ctx.channel().attr(CLIENT_PLATFORM_KEY)?.get()?.let { scope.setTag("client.platform", it) }
            scope.setContexts("websocket", mapOf(
                "remoteAddress" to ctx.channel().remoteAddress()?.toString(),
                "channelId" to ctx.channel().id().asShortText(),
                "userId" to (userId ?: "unauthenticated"),
                "messageType" to (messageType ?: "unknown")
            ))
            Sentry.captureException(e)
        }
    }

    private fun sendJson(ctx: ChannelHandlerContext, data: Any) {
        userSessionManager.sendJsonToChannel(ctx.channel(), objectMapper.writeValueAsString(data))
    }
}
