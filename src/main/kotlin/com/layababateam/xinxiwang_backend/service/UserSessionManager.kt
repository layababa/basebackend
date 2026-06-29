package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.metrics.WebSocketMetrics
import com.layababateam.xinxiwang_backend.netty.ChannelDeviceResolver
import com.layababateam.xinxiwang_backend.netty.ProtocolType
import com.layababateam.xinxiwang_backend.service.push.PushDispatchService
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.util.AttributeKey
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Service
class UserSessionManager(
    private val redisTemplate: StringRedisTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val objectMapper: ObjectMapper,
    private val pushDispatchService: PushDispatchService,
    private val wsMetrics: WebSocketMetrics,
    private val businessMetrics: com.layababateam.xinxiwang_backend.metrics.BusinessMetrics,
    private val clientVersionGate: ClientVersionGate,
    @Value("\${rentmsg.node.id:\${xinxiwang.node.id:node-1}}") val nodeId: String,
    @Value("\${rentmsg.single-node-mode:false}") private val singleNodeMode: Boolean
) : PullLogDeliveryPort, NodeRoutingPort, UserLogConfigPushPort, ChannelDeviceResolver {
    private val log = LoggerFactory.getLogger(UserSessionManager::class.java)

    private val userChannels = ConcurrentHashMap<String, MutableSet<Channel>>()
    private val channelToUser = ConcurrentHashMap<String, String>()
    private val channelToToken = ConcurrentHashMap<String, String>()
    private val channelCapabilities = ConcurrentHashMap<String, Set<String>>()
    private val channelProtocol = ConcurrentHashMap<String, ProtocolType>()
    private val channelToClientVersion = ConcurrentHashMap<String, String>()
    private val channelToClientLogRouteField = ConcurrentHashMap<String, String>()
    // Per-device readSeqId：auth 时从 DeviceSession 读出，供 update_read_point 兜底取
    private val channelToDeviceId = ConcurrentHashMap<String, String>()
    // 热更标签：客户端 auth 时上报 hotpatchTag（Shorebird patch 标识），仅存 in-memory，用于 admin 设备列表展示
    private val channelToHotpatchTag = ConcurrentHashMap<String, String>()

    companion object {
        private const val MAX_CONNECTIONS_PER_USER = 10
        private const val ROUTE_CACHE_TTL_MS = 5_000L
        private const val ROUTE_KEY_PREFIX = "rentmsg:route:"
        private const val NODE_HEARTBEAT_KEY_PREFIX = "rentmsg:node:"
        private const val LEGACY_NODE_HEARTBEAT_KEY_PREFIX = "xinxiwang:node:"
        private const val CLIENT_LOG_ELIGIBLE_KEY_PREFIX = "rentmsg:client-log:eligible:"
        private const val MAX_PENDING_MESSAGES_PER_CHANNEL = 200
        private const val MAX_PENDING_BYTES_PER_CHANNEL = 512 * 1024
        private const val CONVERSATION_UPDATE_COALESCE_MS = 200L
        private val PENDING_BUFFER_KEY = AttributeKey.valueOf<PendingChannelBuffer>("rentmsg.pending.buffer")
        /** 用户路由 SET TTL：每次注册顺延 1 天，防止僵尸用户 route 无限驻留 Redis */
        private val ROUTE_KEY_TTL: Duration = Duration.ofDays(1)
        private val CLIENT_LOG_ELIGIBLE_TTL: Duration = Duration.ofSeconds(45)
    }

    private val routeCache = ConcurrentHashMap<String, Pair<Set<String>, Long>>()
    private val conversationUpdatePending = ConcurrentHashMap<String, PendingConversationUpdate>()
    private val conversationUpdateScheduler = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    }

    private data class PendingChannelBuffer(
        val messages: ArrayDeque<PendingOutboundMessage> = ArrayDeque(),
        var pendingBytes: Int = 0
    )

    private data class PendingOutboundMessage(
        val payload: String,
        val forceJson: Boolean
    )

    private class PendingConversationUpdate(
        @Volatile var message: String,
        @Volatile var skipApns: Boolean,
        @Volatile var excludeChannel: Channel?
    )

    /**
     * 绑定去重在线人数 gauge（ws_online_users）= 本节点 userChannels.size（按 userId 去重）。
     * 用 supplier 回调反转依赖方向（不把本 bean 注入 WebSocketMetrics 构造器），零循环依赖。
     */
    @PostConstruct
    fun bindMetrics() {
        wsMetrics.bindOnlineUsersGauge { userChannels.size }
    }

    // 版本分布：新增 platform / clientVersion 可空参数，供 ws_connections_version gauge 上报使用
    fun register(userId: String, channel: Channel, token: String? = null, capabilities: Set<String> = emptySet(), protocol: ProtocolType = ProtocolType.JSON, platform: String? = null, clientVersion: String? = null, deviceId: String? = null, hotpatchTag: String? = null) {
        val channels = userChannels.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }

        if (channels.size >= MAX_CONNECTIONS_PER_USER) {
            val oldest = channels.firstOrNull()
            if (oldest != null && oldest.isActive) {
                val kickPayload = objectMapper.writeValueAsString(
                    mapOf("type" to "force_offline", "message" to "您的账号在其他设备上登录")
                )
                val kickFrame = when (getProtocol(oldest)) {
                    ProtocolType.JSON -> TextWebSocketFrame(kickPayload)
                    ProtocolType.PROTOBUF -> createProtobufFrame(kickPayload)
                }
                oldest.writeAndFlush(kickFrame).addListener {
                    oldest.close()
                }
                // 走 unregister 唯一出口（必须在 channels.add(channel) 之前）：保证 register/unregister
                // 与 connectionOpened/connectionClosed 严格 1:1 配对——unregister 内部完成全部
                // channelTo* / userChannels / Redis route 清理 + connectionClosed()（同时止漏
                // ws_connections_version 版本 gauge：oldest 的 CLIENT_PLATFORM_KEY/CLIENT_VERSION_KEY
                // 在其 auth 时已 set，版本递减能正确配对）。改前手动拆解漏调 connectionClosed → active 永久泄漏 +1。
                unregister(oldest)
                log.info("Kicked oldest connection for user {} (max {} reached)", userId, MAX_CONNECTIONS_PER_USER)
            }
        }

        channels.add(channel)
        channelToUser[channel.id().asLongText()] = userId
        if (token != null) channelToToken[channel.id().asLongText()] = token
        if (capabilities.isNotEmpty()) channelCapabilities[channel.id().asLongText()] = capabilities
        channelProtocol[channel.id().asShortText()] = protocol
        if (!clientVersion.isNullOrBlank()) channelToClientVersion[channel.id().asLongText()] = clientVersion
        if (deviceId != null) channelToDeviceId[channel.id().asLongText()] = deviceId
        // 记录热更标签（可为 null，老客户端未上报时不存）
        if (!hotpatchTag.isNullOrBlank()) channelToHotpatchTag[channel.id().asLongText()] = hotpatchTag
        val routeKey = "$ROUTE_KEY_PREFIX$userId"
        if (singleNodeMode) {
            redisTemplate.delete(routeKey)
        }
        redisTemplate.opsForSet().add(routeKey, nodeId)
        // 每次 register 顺延 1 天 TTL，保证活跃用户 route 不会被清掉、离线后自动淘汰
        redisTemplate.expire(routeKey, ROUTE_KEY_TTL)
        syncClientLogEligibility(userId, channel)
        routeCache.remove(userId)
        wsMetrics.connectionOpened(platform, clientVersion)
        log.info("User {} registered channel {} on node {} caps={} protocol={}", userId, channel.id().asShortText(), nodeId, capabilities, protocol)
    }

    fun unregister(channel: Channel) {
        val channelLongId = channel.id().asLongText()
        val userId = channelToUser.remove(channelLongId) ?: return
        channelToToken.remove(channelLongId)
        channelCapabilities.remove(channelLongId)
        channelProtocol.remove(channel.id().asShortText())
        channelToClientVersion.remove(channelLongId)
        channelToDeviceId.remove(channelLongId)
        clearClientLogEligibility(userId, channel)
        // 热更标签随断线清理
        channelToHotpatchTag.remove(channelLongId)
        val channels = userChannels[userId]
        channels?.remove(channel)
        if (channels.isNullOrEmpty()) {
            userChannels.remove(userId)
            redisTemplate.opsForSet().remove("$ROUTE_KEY_PREFIX$userId", nodeId)
            routeCache.remove(userId)
        }
        val closePlatform = try { channel.attr(com.layababateam.xinxiwang_backend.netty.NettyWebSocketHandler.CLIENT_PLATFORM_KEY)?.get() } catch (_: Exception) { null }
        val closeVersion = try { channel.attr(com.layababateam.xinxiwang_backend.netty.NettyWebSocketHandler.CLIENT_VERSION_KEY)?.get() } catch (_: Exception) { null }
        wsMetrics.connectionClosed(closePlatform, closeVersion)
        log.info("User {} unregistered channel {}", userId, channel.id().asShortText())
    }

    fun getUserId(channel: Channel): String? =
        channelToUser[channel.id().asLongText()]

    override fun getTokenForChannel(channel: Channel): String? =
        channelToToken[channel.id().asLongText()]

    override fun getDeviceId(channel: Channel): String? =
        channelToDeviceId[channel.id().asLongText()]

    fun getClientVersion(channel: Channel): String? =
        channelToClientVersion[channel.id().asLongText()]

    fun getProtocol(channel: Channel): ProtocolType =
        channelProtocol[channel.id().asShortText()] ?: ProtocolType.JSON

    override fun getChannels(userId: String): Set<Channel> =
        userChannels[userId] ?: emptySet()

    fun isOnline(userId: String): Boolean =
        userChannels[userId]?.isNotEmpty() == true

    fun isOnlineGlobally(userId: String): Boolean {
        if (isOnline(userId)) return true
        if (singleNodeMode) return false
        val nodes = getCachedRouteNodes(userId)
        return nodes.isNotEmpty()
    }

    fun userSupportsBatch(userId: String): Boolean {
        val channels = getChannels(userId)
        if (channels.isEmpty()) return false
        return channels.all { channelCapabilities[it.id().asLongText()]?.contains("batch") == true }
    }

    override fun getEligibleClientLogDeviceIds(userId: String): Set<String> {
        refreshClientLogEligibility(userId)
        return try {
            redisTemplate.opsForHash<String, String>()
                .values(clientLogEligibleKey(userId))
                .filter { it.isNotBlank() }
                .toSet()
        } catch (e: Exception) {
            log.warn("Client-log eligible snapshot degraded for user {}: {}", userId, e.message)
            getEligibleClientLogChannels(userId).mapNotNull { getDeviceId(it) }.toSet()
        }
    }

    override fun pushClientLogConfigToEligibleUser(userId: String, message: String): Int {
        val localCount = pushClientLogConfigToLocalEligibleUser(userId, message)
        val remoteNodes = getRemoteRouteNodes(userId)
        for (targetNode in remoteNodes) {
            publishRabbit(
                RabbitMQConfig.ROUTE_EXCHANGE,
                "node.$targetNode",
                mapOf(
                    "action" to "client_log_config_updated",
                    "targetUserId" to userId,
                    "message" to message
                ),
                "client-log-config user=$userId node=$targetNode"
            )
        }
        if (remoteNodes.isNotEmpty()) {
            refreshClientLogEligibility(userId)
        }
        return getEligibleClientLogDeviceIds(userId).size.coerceAtLeast(localCount)
    }

    override fun pushClientLogConfigToLocalEligibleUser(userId: String, message: String): Int {
        val eligible = getEligibleClientLogChannels(userId)
        if (eligible.isEmpty()) return 0
        sendToChannels(eligible, message)
        return eligible.mapNotNull { getDeviceId(it) }.toSet().size
    }

    private fun getEligibleClientLogChannels(userId: String): Set<Channel> =
        getChannels(userId)
            .filter { channel ->
                val channelId = channel.id().asLongText()
                val caps = channelCapabilities[channelId] ?: emptySet()
                clientVersionGate.isEligible(
                    channelToClientVersion[channelId],
                    supportsClientLogConfig = caps.contains("client_log_config")
                )
            }
            .toSet()

    override fun refreshClientLogEligibility(userId: String?) {
        val targets: Map<String, Collection<Channel>> = if (userId != null) {
            mapOf(userId to (userChannels[userId] ?: emptySet<Channel>()))
        } else {
            userChannels
        }
        targets.forEach { (targetUserId, channels) ->
            channels.forEach { channel -> syncClientLogEligibility(targetUserId, channel) }
        }
    }

    private fun syncClientLogEligibility(userId: String, channel: Channel) {
        val channelId = channel.id().asLongText()
        val field = clientLogRouteField(channel)
        val key = clientLogEligibleKey(userId)
        val deviceId = channelToDeviceId[channelId]
        val caps = channelCapabilities[channelId] ?: emptySet()
        val eligible = channel.isActive &&
            !deviceId.isNullOrBlank() &&
            clientVersionGate.isEligible(
                channelToClientVersion[channelId],
                supportsClientLogConfig = caps.contains("client_log_config")
            )
        try {
            if (eligible) {
                redisTemplate.opsForHash<String, String>().put(key, field, deviceId)
                redisTemplate.expire(key, CLIENT_LOG_ELIGIBLE_TTL)
                channelToClientLogRouteField[channelId] = field
            } else {
                val previousField = channelToClientLogRouteField.remove(channelId)
                if (previousField != null) {
                    redisTemplate.opsForHash<String, String>().delete(key, previousField)
                }
            }
        } catch (e: Exception) {
            log.warn("Client-log eligibility sync degraded for user {} channel {}: {}", userId, channel.id().asShortText(), e.message)
        }
    }

    private fun clearClientLogEligibility(userId: String, channel: Channel) {
        val channelId = channel.id().asLongText()
        val field = channelToClientLogRouteField.remove(channelId) ?: clientLogRouteField(channel)
        try {
            redisTemplate.opsForHash<String, String>().delete(clientLogEligibleKey(userId), field)
        } catch (e: Exception) {
            log.warn("Client-log eligibility cleanup degraded for user {} channel {}: {}", userId, channel.id().asShortText(), e.message)
        }
    }

    private fun clientLogEligibleKey(userId: String): String = "$CLIENT_LOG_ELIGIBLE_KEY_PREFIX$userId"

    private fun clientLogRouteField(channel: Channel): String = "$nodeId:${channel.id().asLongText()}"

    override fun findChannelByToken(userId: String, token: String): Channel? {
        return getChannels(userId).find { channelToToken[it.id().asLongText()] == token }
    }

    /**
     * 根据 token 取出该在线连接的热更标签（Shorebird patch 标识）。
     * admin 用户详情设备列表接口用此方法按 token 拼出 "热更" 列数据。
     */
    fun getHotpatchTagByToken(userId: String, token: String): String? {
        val channel = findChannelByToken(userId, token) ?: return null
        return channelToHotpatchTag[channel.id().asLongText()]
    }

    fun supportsClientLogConfigByToken(userId: String, token: String): Boolean {
        val channel = findChannelByToken(userId, token) ?: return false
        val channelId = channel.id().asLongText()
        val caps = channelCapabilities[channelId] ?: emptySet()
        return clientVersionGate.isEligible(
            channelToClientVersion[channelId],
            supportsClientLogConfig = caps.contains("client_log_config")
        )
    }

    fun pushToUser(userId: String, message: String, skipApns: Boolean = false, messageType: String? = null): Boolean {
        val conversationId = if (messageType == "conversation_updated" || message.contains("conversation_updated")) {
            extractConversationUpdateId(message)
        } else {
            null
        }
        if (conversationId != null) {
            enqueueConversationUpdate(userId, conversationId, message, skipApns, excludeChannel = null)
            return isOnlineGlobally(userId)
        }
        return pushToUserDirect(userId, message, skipApns, messageType)
    }

    private fun pushToUserDirect(userId: String, message: String, skipApns: Boolean = false, messageType: String? = null): Boolean {
        val localChannels = getChannels(userId)
        var deliveredViaWs = false
        if (localChannels.isNotEmpty()) {
            sendToChannels(localChannels, message)
            deliveredViaWs = true
        }

        // Check for remote nodes (with local cache)
        val remoteNodes = getRemoteRouteNodes(userId)
        if (remoteNodes.isNotEmpty()) {
            deliveredViaWs = true
            for (targetNode in remoteNodes) {
                publishRabbit(
                    RabbitMQConfig.ROUTE_EXCHANGE,
                    "node.$targetNode",
                    mapOf("targetUserId" to userId, "message" to message),
                    "route user=$userId node=$targetNode"
                )
            }
        }

        // APNs 推送改為透過 MQ 非同步處理，避免大群聊時阻塞
        if (!skipApns) {
            val pushableTypes = setOf("new_message", "friend_request_notification", "friend_accepted_notification", "incoming_call")
            val type = messageType ?: try {
                (objectMapper.readValue(message, Map::class.java)["type"] as? String)
            } catch (_: Exception) { null }
            if (type != null && type in pushableTypes) {
                val onlineAuthTokens = getOnlineAuthTokens(userId)
                publishRabbit(
                    RabbitMQConfig.APNS_PUSH_QUEUE,
                    mapOf(
                        "userId" to userId,
                        "wsMessage" to message,
                        "onlineAuthTokens" to onlineAuthTokens.toList()
                    ),
                    "apns user=$userId type=$type"
                )
            }
        }

        return deliveredViaWs
    }

    /**
     * Returns the set of auth (login) tokens for all currently active WS channels of a user.
     */
    fun getOnlineAuthTokens(userId: String): Set<String> {
        val channels = getChannels(userId)
        return channels.mapNotNull { channelToToken[it.id().asLongText()] }.toSet()
    }


    fun pushToUserExcluding(userId: String, message: String, excludeChannel: Channel) {
        val conversationId = extractConversationUpdateId(message)
        if (conversationId != null) {
            enqueueConversationUpdate(userId, conversationId, message, skipApns = true, excludeChannel = excludeChannel)
            return
        }
        pushToUserExcludingDirect(userId, message, excludeChannel)
    }

    private fun pushToUserExcludingDirect(userId: String, message: String, excludeChannel: Channel) {
        val localChannels = getChannels(userId).filter { it != excludeChannel }
        if (localChannels.isNotEmpty()) {
            sendToChannels(localChannels, message)
            log.debug("Pushed excluding to {} local channels for user {}", localChannels.size, userId)
        }

        // Route to remote nodes (excludeChannel is local-only, so no exclusion needed remotely)
        val remoteNodes = getRemoteRouteNodes(userId)
        if (remoteNodes.isNotEmpty()) {
            for (targetNode in remoteNodes) {
                publishRabbit(
                    RabbitMQConfig.ROUTE_EXCHANGE,
                    "node.$targetNode",
                    mapOf("targetUserId" to userId, "message" to message),
                    "route-excluding user=$userId node=$targetNode"
                )
            }
            log.debug("Routed excluding-message for user {} to {} remote nodes", userId, remoteNodes.size)
        }
    }

    private fun enqueueConversationUpdate(
        userId: String,
        conversationId: String,
        message: String,
        skipApns: Boolean,
        excludeChannel: Channel?
    ) {
        val key = "$userId|$conversationId"
        val pending = PendingConversationUpdate(message, skipApns, excludeChannel)
        val previous = conversationUpdatePending.putIfAbsent(key, pending)
        if (previous != null) {
            previous.message = message
            previous.skipApns = skipApns
            previous.excludeChannel = excludeChannel
            return
        }
        conversationUpdateScheduler.schedule({
            val latest = conversationUpdatePending.remove(key) ?: return@schedule
            val excluded = latest.excludeChannel
            if (excluded != null) {
                pushToUserExcludingDirect(userId, latest.message, excluded)
            } else {
                pushToUserDirect(userId, latest.message, skipApns = latest.skipApns, messageType = "conversation_updated")
            }
        }, CONVERSATION_UPDATE_COALESCE_MS, TimeUnit.MILLISECONDS)
    }

    private fun extractConversationUpdateId(message: String): String? {
        return try {
            val root = objectMapper.readTree(message)
            if (root.get("type")?.asText() != "conversation_updated") return null
            val data = root.get("data") ?: return null
            data.get("id")?.asText()?.takeIf { it.isNotBlank() }
                ?: data.get("conversationId")?.asText()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun pushEphemeralToLocalUser(userId: String, message: String): Boolean {
        val localChannels = getChannels(userId)
        if (localChannels.isEmpty()) return false
        sendToChannels(localChannels, message)
        return true
    }

    fun pushEphemeralToLocalUsers(userIds: Collection<String>, message: String, excludeUserId: String? = null): Int {
        if (userIds.isEmpty() || userChannels.isEmpty()) return 0
        var pushed = 0

        if (userIds.size <= userChannels.size) {
            userIds.forEach { userId ->
                if (userId != excludeUserId && pushEphemeralToLocalUser(userId, message)) {
                    pushed++
                }
            }
            return pushed
        }

        val targetUserIds = userIds.toHashSet()
        userChannels.forEach { (userId, channels) ->
            if (userId != excludeUserId && userId in targetUserIds && channels.isNotEmpty()) {
                sendToChannels(channels, message)
                pushed++
            }
        }
        return pushed
    }

    fun forceDisconnectByToken(userId: String, token: String, reason: String = "Session terminated") {
        val channel = findChannelByToken(userId, token)
        if (channel != null && channel.isActive) {
            val payload = objectMapper.writeValueAsString(
                mapOf("type" to "force_offline", "message" to reason)
            )
            val frame = when (getProtocol(channel)) {
                ProtocolType.JSON -> TextWebSocketFrame(payload)
                ProtocolType.PROTOBUF -> createProtobufFrame(payload)
            }
            channel.writeAndFlush(frame).addListener { channel.close() }
            return
        }

        // 本地没找到，通知远程节点断开
        val nodes = getRemoteRouteNodes(userId)
        for (targetNode in nodes) {
            publishRabbit(
                RabbitMQConfig.ROUTE_EXCHANGE,
                "node.$targetNode",
                mapOf("action" to "force_disconnect_token", "targetUserId" to userId, "token" to token, "reason" to reason),
                "force-disconnect-token user=$userId node=$targetNode"
            )
        }
    }

    /**
     * 断开指定用户的所有 WebSocket 连接（用于注销账户），支持跨节点
     */
    fun disconnectUser(userId: String) {
        disconnectUserLocal(userId)

        // 通知远程节点也断开该用户
        val nodes = getRemoteRouteNodes(userId)
        for (targetNode in nodes) {
            publishRabbit(
                RabbitMQConfig.ROUTE_EXCHANGE,
                "node.$targetNode",
                mapOf("action" to "disconnect_user", "targetUserId" to userId),
                "disconnect-user user=$userId node=$targetNode"
            )
        }
    }

    override fun disconnectUserLocal(userId: String) {
        val channels = getChannels(userId).toList()
        channels.forEach { channel ->
            if (channel.isActive) {
                val payload = objectMapper.writeValueAsString(
                    mapOf("type" to "force_offline", "message" to "您的账户已被注销")
                )
                val frame = when (getProtocol(channel)) {
                    ProtocolType.JSON -> TextWebSocketFrame(payload)
                    ProtocolType.PROTOBUF -> createProtobufFrame(payload)
                }
                channel.writeAndFlush(frame).addListener { channel.close() }
            }
            unregister(channel)
        }
    }

    private fun createProtobufFrame(message: String): BinaryWebSocketFrame {
        val parsed = try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(message, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) { emptyMap() }
        val builder = com.layababateam.xinxiwang_backend.proto.WsEnvelope.newBuilder()
            .setType((parsed["type"] as? String) ?: "")
            .setJsonFallback(message)
        (parsed["requestId"] as? String)?.let { builder.setRequestId(it) }
        return BinaryWebSocketFrame(Unpooled.wrappedBuffer(builder.build().toByteArray()))
    }

    private fun publishRabbit(queue: String, payload: Any, action: String) {
        try {
            rabbitPublishService.send(queue, payload, action)
        } catch (e: Exception) {
            log.warn("Rabbit publish degraded: action={} error={}", action, e.message)
        }
    }

    private fun publishRabbit(exchange: String, routingKey: String, payload: Any, action: String) {
        try {
            rabbitPublishService.send(exchange, routingKey, payload, action)
        } catch (e: Exception) {
            log.warn("Rabbit publish degraded: action={} error={}", action, e.message)
        }
    }

    private fun sendToChannels(channels: Collection<Channel>, message: String) {
        channels.forEach { ch -> sendToChannel(ch, message) }
    }

    fun sendToChannel(channel: Channel, message: String) {
        if (!channel.isActive) return
        channel.eventLoop().execute {
            if (!channel.isActive) return@execute
            val buffer = getOrCreatePendingBuffer(channel)
            if (buffer.messages.isNotEmpty() || !channel.isWritable) {
                enqueuePendingMessage(channel, buffer, message, forceJson = false)
                if (channel.isWritable) {
                    flushPendingMessagesInternal(channel, buffer)
                }
                return@execute
            }

            writeFrame(channel, message, flush = true)
            wsMetrics.messageSent()
        }
    }

    override fun sendJsonToChannel(channel: Channel, message: String) {
        if (!channel.isActive) return
        channel.eventLoop().execute {
            if (!channel.isActive) return@execute
            val buffer = getOrCreatePendingBuffer(channel)
            if (buffer.messages.isNotEmpty() || !channel.isWritable) {
                enqueuePendingMessage(channel, buffer, message, forceJson = true)
                if (channel.isWritable) flushPendingMessagesInternal(channel, buffer)
                return@execute
            }

            val frame = TextWebSocketFrame(message)
            businessMetrics.recordWsBytes("out", sniffKind(message), frame.content().readableBytes().toLong())
            channel.writeAndFlush(frame)
        }
    }

    fun flushPendingMessages(channel: Channel) {
        if (!channel.isActive) return
        channel.eventLoop().execute {
            if (!channel.isActive || !channel.isWritable) return@execute
            val buffer = channel.attr(PENDING_BUFFER_KEY).get() ?: return@execute
            flushPendingMessagesInternal(channel, buffer)
        }
    }

    private fun getOrCreatePendingBuffer(channel: Channel): PendingChannelBuffer {
        val attr = channel.attr(PENDING_BUFFER_KEY)
        val existing = attr.get()
        if (existing != null) return existing
        val created = PendingChannelBuffer()
        val previous = attr.setIfAbsent(created)
        return previous ?: created
    }

    private fun enqueuePendingMessage(
        channel: Channel,
        buffer: PendingChannelBuffer,
        message: String,
        forceJson: Boolean
    ) {
        val messageBytes = message.toByteArray(Charsets.UTF_8).size
        if (buffer.messages.size >= MAX_PENDING_MESSAGES_PER_CHANNEL ||
            buffer.pendingBytes + messageBytes > MAX_PENDING_BYTES_PER_CHANNEL
        ) {
            log.info(
                "Channel {} pending buffer overflow (messages={}, bytes={}), closing slow client",
                channel.id().asShortText(),
                buffer.messages.size,
                buffer.pendingBytes
            )
            buffer.messages.clear()
            buffer.pendingBytes = 0
            channel.close()
            return
        }

        buffer.messages.addLast(PendingOutboundMessage(message, forceJson))
        buffer.pendingBytes += messageBytes
        log.debug(
            "Channel {} not writable, buffered message (messages={}, bytes={})",
            channel.id().asShortText(),
            buffer.messages.size,
            buffer.pendingBytes
        )
    }

    private fun flushPendingMessagesInternal(channel: Channel, buffer: PendingChannelBuffer) {
        if (!channel.isActive || !channel.isWritable || buffer.messages.isEmpty()) return

        var wroteAny = false
        while (channel.isActive && channel.isWritable && buffer.messages.isNotEmpty()) {
            val next = buffer.messages.removeFirst()
            buffer.pendingBytes -= next.payload.toByteArray(Charsets.UTF_8).size
            if (next.forceJson) {
                val frame = TextWebSocketFrame(next.payload)
                businessMetrics.recordWsBytes("out", sniffKind(next.payload), frame.content().readableBytes().toLong())
                channel.write(frame)
            } else {
                writeFrame(channel, next.payload, flush = false)
                wsMetrics.messageSent()
            }
            wroteAny = true
        }

        if (wroteAny) {
            channel.flush()
        }
    }

    private fun writeFrame(channel: Channel, message: String, flush: Boolean) {
        val frame = when (getProtocol(channel)) {
            ProtocolType.JSON -> TextWebSocketFrame(message)
            ProtocolType.PROTOBUF -> createProtobufFrame(message)
        }
        val outBytes = frame.content().readableBytes().toLong()
        businessMetrics.recordWsBytes("out", sniffKind(message), outBytes)
        if (flush) {
            channel.writeAndFlush(frame)
        } else {
            channel.write(frame)
        }
    }

    /** 心跳嗅探：仅 short text 含 ping/pong 视为 heartbeat，其他都是 business */
    private fun sniffKind(message: String): String =
        if (message.length < 60 && (message.contains("\"ping\"") || message.contains("\"pong\""))) "heartbeat" else "business"

    private fun getCachedRouteNodes(userId: String): Set<String> {
        val cached = routeCache[userId]
        val now = System.currentTimeMillis()
        if (cached != null && cached.second > now) return cached.first
        val routeKey = "$ROUTE_KEY_PREFIX$userId"
        val nodes = redisTemplate.opsForSet().members(routeKey)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        if (nodes.isEmpty()) {
            routeCache.remove(userId)
            return emptySet()
        }
        val validation = resolveLiveNodes(nodes)
        if (validation.stale.isNotEmpty()) {
            cleanupStaleRouteNodes(userId, validation.stale)
        }
        // 不快取空結果 — 避免負快取污染 5 秒導致 isOnlineGlobally 誤判為離線。
        // 場景：callee 剛好在 register/unregister 邊界，第一次查到空，後續 5 秒內
        // 任何來電都會被判為離線。incoming_call 走 APNs 而不走 WS，但客戶端若
        // 已連線就收不到推送（iOS 前景抑制）→ 來電丟失。
        if (validation.live.isNotEmpty()) {
            routeCache[userId] = validation.live to (now + ROUTE_CACHE_TTL_MS)
        } else {
            routeCache.remove(userId)
        }
        return validation.live
    }

    private fun getRemoteRouteNodes(userId: String): List<String> {
        if (singleNodeMode) return emptyList()
        return getCachedRouteNodes(userId).filter { it != nodeId }
    }

    @PreDestroy
    fun shutdown() {
        log.info("Graceful shutdown: notifying {} online users", userChannels.size)
        conversationUpdateScheduler.shutdownNow()
        // Lettuce / Netty event loop 可能已先於本 bean 被銷毀，shutdown 競態下
        // Redis 操作會拋 IllegalStateException / RejectedExecutionException 等。
        // 包裹後降級為 warn，避免 Spring 將 destroy 失敗上報為 error。
        try {
            userChannels.keys.forEach { userId ->
                try {
                    redisTemplate.opsForSet().remove("$ROUTE_KEY_PREFIX$userId", nodeId)
                } catch (_: Throwable) {}
            }
            // 立即删除节点心跳 key，不等 TTL 过期
            redisTemplate.delete("$NODE_HEARTBEAT_KEY_PREFIX$nodeId")
            redisTemplate.delete("$LEGACY_NODE_HEARTBEAT_KEY_PREFIX$nodeId")
            log.info("Removed node heartbeat keys for {}", nodeId)
        } catch (e: Throwable) {
            log.warn("Redis cleanup skipped during shutdown: {}", e.javaClass.simpleName)
        }

        val shutdownPayload = objectMapper.writeValueAsString(
            mapOf("type" to "server_shutdown", "message" to "服务器正在维护，请稍后重连")
        )
        userChannels.values.flatten().forEach { channel ->
            if (channel.isActive) {
                val frame = when (getProtocol(channel)) {
                    ProtocolType.JSON -> TextWebSocketFrame(shutdownPayload)
                    ProtocolType.PROTOBUF -> createProtobufFrame(shutdownPayload)
                }
                channel.writeAndFlush(frame)
            }
        }
        Thread.sleep(3000)
        userChannels.values.flatten().forEach { it.close() }
        log.info("Graceful shutdown complete")
    }

    fun preloadRoutes(userIds: List<String>) {
        val now = System.currentTimeMillis()
        val uncached = userIds.filter { userId ->
            val cached = routeCache[userId]
            cached == null || cached.second <= now
        }
        if (uncached.isEmpty()) return
        val results = redisTemplate.executePipelined { connection ->
            @Suppress("UNCHECKED_CAST")
            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
            uncached.forEach { userId ->
                connection.setCommands().sMembers(
                    keySerializer.serialize("$ROUTE_KEY_PREFIX$userId")!!
                )
            }
            null
        }
        val expireAt = now + ROUTE_CACHE_TTL_MS
        val rawNodesByUser = mutableMapOf<String, Set<String>>()
        uncached.forEachIndexed { index, userId ->
            @Suppress("UNCHECKED_CAST")
            val nodes = when (val result = results.getOrNull(index)) {
                is Set<*> -> result.mapNotNull { it as? String }.filter { it.isNotBlank() }.toSet()
                else -> emptySet()
            }
            rawNodesByUser[userId] = nodes
        }
        val allNodes = rawNodesByUser.values.flatten().toSet()
        val liveNodes = resolveLiveNodes(allNodes).live
        rawNodesByUser.forEach { (userId, rawNodes) ->
            val filteredNodes = rawNodes.intersect(liveNodes)
            if (filteredNodes.isNotEmpty()) {
                routeCache[userId] = filteredNodes to expireAt
            } else {
                routeCache.remove(userId)
            }
            val staleNodes = rawNodes - filteredNodes
            if (staleNodes.isNotEmpty()) {
                cleanupStaleRouteNodes(userId, staleNodes)
            }
        }
    }

    private data class RouteValidationResult(
        val live: Set<String>,
        val stale: Set<String>,
    )

    private fun resolveLiveNodes(nodes: Set<String>): RouteValidationResult {
        if (nodes.isEmpty()) return RouteValidationResult(emptySet(), emptySet())
        val uniqueNodes = nodes.filter { it.isNotBlank() }.distinct()
        if (uniqueNodes.isEmpty()) return RouteValidationResult(emptySet(), emptySet())
        val results = redisTemplate.executePipelined { connection ->
            @Suppress("UNCHECKED_CAST")
            val keySerializer = redisTemplate.keySerializer as RedisSerializer<String>
            uniqueNodes.forEach { node ->
                connection.keyCommands().exists(
                    keySerializer.serialize("$NODE_HEARTBEAT_KEY_PREFIX$node")!!,
                )
                connection.keyCommands().exists(
                    keySerializer.serialize("$LEGACY_NODE_HEARTBEAT_KEY_PREFIX$node")!!,
                )
            }
            null
        }
        val liveNodes = LinkedHashSet<String>()
        val staleNodes = LinkedHashSet<String>()
        uniqueNodes.forEachIndexed { index, node ->
            val currentAlive = asExistsFlag(results.getOrNull(index * 2))
            val legacyAlive = asExistsFlag(results.getOrNull(index * 2 + 1))
            if (node == nodeId || currentAlive || legacyAlive) {
                liveNodes.add(node)
            } else {
                staleNodes.add(node)
            }
        }
        return RouteValidationResult(liveNodes, staleNodes)
    }

    private fun cleanupStaleRouteNodes(userId: String, staleNodes: Set<String>) {
        if (staleNodes.isEmpty()) return
        try {
            redisTemplate.opsForSet().remove("$ROUTE_KEY_PREFIX$userId", *staleNodes.toTypedArray())
            log.info("Removed {} stale route nodes for user {}", staleNodes.size, userId)
        } catch (e: Exception) {
            log.warn("Failed to remove stale route nodes for user {}: {}", userId, e.message)
        }
    }

    private fun asExistsFlag(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toLong() > 0
        is String -> value != "0"
        else -> false
    }
}
