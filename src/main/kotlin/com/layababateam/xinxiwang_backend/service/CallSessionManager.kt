package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.FriendshipRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class CallSession(
    val roomId: Int,
    val callerId: String,
    val calleeId: String,
    val callType: Int, // 0=audio, 1=video
    val startedAt: Long = System.currentTimeMillis(),
    val answeredAt: Long? = null,
    val acceptedDeviceId: String? = null,
)

@Service
class CallSessionManager(
    private val messageService: MessageService,
    private val friendshipRepository: FriendshipRepository,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
    private val userSessionManager: UserSessionManager,
    private val audit: CallSessionAudit,
    private val businessMetrics: com.layababateam.xinxiwang_backend.metrics.BusinessMetrics,
) : CallRoomProbeSessionPort, PendingCallPort, CallStateLookupPort {
    private val log = LoggerFactory.getLogger(CallSessionManager::class.java)

    private val ringingTimeouts = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "call-timeout").apply { isDaemon = true }
    }

    companion object {
        private const val SESSION_KEY_PREFIX = "rentmsg:call_session:"
        private const val USER_CALL_KEY_PREFIX = "rentmsg:user_call:"
        private const val ACTIVE_SESSIONS_SET = "rentmsg:active_call_sessions"
        private const val SESSION_TTL_SECONDS = 600L // 10 分钟：未接听时的兜底过期
        private const val ACTIVE_SESSION_TTL_SECONDS = 86400L // 24 小时：接听后续期，支持长时间通话
        private const val PENDING_CALL_KEY_PREFIX = "rentmsg:pending_call:"
        private const val CANCEL_MARKER_KEY_PREFIX = "rentmsg:call_cancel:"
        private const val PENDING_CALL_TTL_SECONDS = 65L
        private const val CANCEL_MARKER_TTL_SECONDS = 30L
        private const val RINGING_TIMEOUT_SECONDS = 60L
        private const val CALLING_KEY_PREFIX = "rentmsg:call_calling:"
        private const val CALLING_TTL_SECONDS = 35L

        /**
         * Lua 脚本：原子性 CAS 标记通话已接听。
         * KEYS[1] = session key
         * ARGV[1] = answeredAt 时间戳
         * ARGV[2] = acceptedDeviceId (可为空字符串)
         * 返回 1 = 成功，0 = 已被抢答或不存在
         */
        private const val MARK_ANSWERED_LUA = """
            local session = redis.call('GET', KEYS[1])
            if not session then return 0 end
            local data = cjson.decode(session)
            if data['answeredAt'] then return 0 end
            data['answeredAt'] = tonumber(ARGV[1])
            local deviceId = ARGV[2]
            if deviceId ~= '' then data['acceptedDeviceId'] = deviceId end
            local ttl = tonumber(ARGV[3])
            redis.call('SET', KEYS[1], cjson.encode(data), 'EX', ttl)
            return 1
        """

        private val markAnsweredScript = DefaultRedisScript(MARK_ANSWERED_LUA, Long::class.java)
    }

    // ─── Redis session CRUD ──────────────────────────────────────────────

    private fun sessionKey(roomId: Int) = "$SESSION_KEY_PREFIX$roomId"
    private fun userCallKey(userId: String) = "$USER_CALL_KEY_PREFIX$userId"
    private fun callingKey(userId: String) = "$CALLING_KEY_PREFIX$userId"

    fun updateCallingHeartbeat(userId: String, roomId: Int) {
        redisTemplate.opsForValue().set(callingKey(userId), roomId.toString(), CALLING_TTL_SECONDS, TimeUnit.SECONDS)
    }

    fun isCallingActive(userId: String): Boolean {
        return redisTemplate.hasKey(callingKey(userId))
    }

    private fun saveSession(session: CallSession) {
        val json = objectMapper.writeValueAsString(session)
        redisTemplate.opsForValue().set(sessionKey(session.roomId), json, SESSION_TTL_SECONDS, TimeUnit.SECONDS)
        // 反向索引：userId -> roomId
        redisTemplate.opsForValue().set(userCallKey(session.callerId), session.roomId.toString(), SESSION_TTL_SECONDS, TimeUnit.SECONDS)
        redisTemplate.opsForValue().set(userCallKey(session.calleeId), session.roomId.toString(), SESSION_TTL_SECONDS, TimeUnit.SECONDS)
        // 追踪活跃会话 roomId（替代 SCAN，兼容 ElastiCache Serverless）
        redisTemplate.opsForSet().add(ACTIVE_SESSIONS_SET, session.roomId.toString())
    }

    private fun loadSession(roomId: Int): CallSession? {
        val json = redisTemplate.opsForValue().get(sessionKey(roomId)) ?: return null
        return try {
            objectMapper.readValue(json, CallSession::class.java)
        } catch (e: Exception) {
            log.warn("Failed to deserialize call session for roomId={}: {}", roomId, e.message)
            null
        }
    }

    private fun removeSession(roomId: Int): CallSession? {
        val session = loadSession(roomId) ?: return null
        redisTemplate.delete(sessionKey(roomId))
        redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_SET, roomId.toString())
        // 仅当反向索引指向当前 roomId 时才删除（防止误删其他通话的索引）
        removeUserCallIndex(session.callerId, roomId)
        removeUserCallIndex(session.calleeId, roomId)
        return session
    }

    private fun removeUserCallIndex(userId: String, roomId: Int) {
        val key = userCallKey(userId)
        val storedRoomId = redisTemplate.opsForValue().get(key)
        if (storedRoomId == roomId.toString()) {
            redisTemplate.delete(key)
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────

    fun createSession(roomId: Int, callerId: String, calleeId: String, callType: Int): CallSession {
        val session = CallSession(roomId = roomId, callerId = callerId, calleeId = calleeId, callType = callType)
        saveSession(session)
        businessMetrics.incrementCallActive()
        log.info("Call session created: roomId={} caller={} callee={} type={}", roomId, callerId, calleeId, callType)
        audit.log("session_created", roomId, callerId, mapOf(
            "calleeId" to calleeId,
            "callType" to callType,
        ))
        scheduleRingingTimeout(roomId)
        return session
    }

    /**
     * Schedule a server-side timeout for unanswered calls.
     * 仅由创建会话的节点执行，超时时从 Redis 读取最新状态。
     */
    private fun scheduleRingingTimeout(roomId: Int) {
        val future = scheduler.schedule({
            try {
                val session = loadSession(roomId) ?: return@schedule
                if (session.answeredAt != null) return@schedule

                log.info("Ringing timeout for roomId={}, ending session", roomId)
                audit.log("ringing_timeout", roomId, session.callerId, mapOf(
                    "calleeId" to session.calleeId,
                ))

                val callerMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "call_ended",
                    "data" to mapOf("roomId" to roomId, "userId" to session.calleeId, "reason" to "timeout")
                ))
                userSessionManager.pushToUser(session.callerId, callerMsg)

                val calleeMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "call_cancelled",
                    "data" to mapOf("roomId" to roomId, "userId" to session.callerId)
                ))
                userSessionManager.pushToUser(session.calleeId, calleeMsg)

                endSession(roomId, "timeout")
            } catch (e: Exception) {
                log.warn("Error in ringing timeout for roomId={}: {}", roomId, e.message)
            }
        }, RINGING_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        ringingTimeouts[roomId] = future
    }

    private fun cancelRingingTimeout(roomId: Int) {
        ringingTimeouts.remove(roomId)?.cancel(false)
    }

    /**
     * 通过 Redis Lua 脚本原子性标记通话已接听，保证多实例下只有一个设备能抢答成功。
     */
    fun markAnswered(roomId: Int, deviceId: String? = null): Boolean {
        val result = redisTemplate.execute(
            markAnsweredScript,
            listOf(sessionKey(roomId)),
            System.currentTimeMillis().toString(),
            deviceId ?: "",
            ACTIVE_SESSION_TTL_SECONDS.toString()
        )
        val success = result == 1L
        val sessionSnap = loadSession(roomId)
        if (success) {
            cancelRingingTimeout(roomId)
            // 续期反向索引，与 session 保持一致
            if (sessionSnap != null) {
                redisTemplate.expire(userCallKey(sessionSnap.callerId), ACTIVE_SESSION_TTL_SECONDS, TimeUnit.SECONDS)
                redisTemplate.expire(userCallKey(sessionSnap.calleeId), ACTIVE_SESSION_TTL_SECONDS, TimeUnit.SECONDS)
            }
            audit.log("answered", roomId, sessionSnap?.calleeId, mapOf(
                "deviceId" to (deviceId ?: "-"),
                "callerId" to (sessionSnap?.callerId ?: "-"),
            ))
        } else {
            audit.log("answer_rejected", roomId, sessionSnap?.calleeId, mapOf(
                "deviceId" to (deviceId ?: "-"),
                "reason" to if (sessionSnap == null) "session_not_found" else "already_answered_by_other_device",
                "existingDeviceId" to (sessionSnap?.acceptedDeviceId ?: "-"),
            ))
        }
        return success
    }

    fun getSession(roomId: Int): CallSession? = loadSession(roomId)

    override fun getCallRoomProbeSession(roomId: Int): CallRoomProbeSession? =
        getSession(roomId)?.let { session ->
            CallRoomProbeSession(
                roomId = session.roomId,
                callerId = session.callerId,
                calleeId = session.calleeId,
                answered = session.answeredAt != null,
            )
        }

    override fun hasActiveCallSession(roomId: Int): Boolean = getSession(roomId) != null

    /**
     * 通过反向索引快速查找用户当前的通话会话。
     */
    fun findSessionByUser(userId: String): CallSession? {
        val roomIdStr = redisTemplate.opsForValue().get(userCallKey(userId)) ?: return null
        val roomId = roomIdStr.toIntOrNull() ?: return null
        return loadSession(roomId)
    }

    override fun getCallState(userId: String): CallStateSnapshot {
        val session = findSessionByUser(userId)
            ?: return CallStateSnapshot(inCall = false, roomId = null, peerId = null, answered = false)
        val peerId = if (session.callerId == userId) session.calleeId else session.callerId
        return CallStateSnapshot(
            inCall = true,
            roomId = session.roomId,
            peerId = peerId,
            answered = session.answeredAt != null,
        )
    }

    // ─── Pending call for offline users ─────────────────────────────────────

    fun storePendingCall(calleeId: String, payload: String) {
        val key = "$PENDING_CALL_KEY_PREFIX$calleeId"
        redisTemplate.opsForValue().set(key, payload, PENDING_CALL_TTL_SECONDS, TimeUnit.SECONDS)
        log.info("Stored pending call for user {}", calleeId)
    }

    fun popPendingCall(userId: String): String? {
        val key = "$PENDING_CALL_KEY_PREFIX$userId"
        val payload = redisTemplate.opsForValue().getAndDelete(key)
        if (payload != null) {
            log.info("Popped pending call for user {}", userId)
        }
        return payload
    }

    /**
     * 非破壞性讀取 — 留在 Redis 直到 call_accept/reject/cancel/timeout 觸發 clearPendingCall。
     * 用於 auth handler / check_pending_call，避免一次性 pop 後消息遺失就永久找不到。
     */
    override fun peekPendingCall(userId: String): String? {
        val key = "$PENDING_CALL_KEY_PREFIX$userId"
        return redisTemplate.opsForValue().get(key)
    }

    override fun clearPendingCall(userId: String) {
        val key = "$PENDING_CALL_KEY_PREFIX$userId"
        val deleted = redisTemplate.delete(key)
        if (deleted) {
            log.info("Cleared pending call for user {}", userId)
        }
    }

    fun storeCancelMarker(calleeId: String, payload: String) {
        val key = "$CANCEL_MARKER_KEY_PREFIX$calleeId"
        redisTemplate.opsForValue().set(key, payload, CANCEL_MARKER_TTL_SECONDS, TimeUnit.SECONDS)
        log.info("Stored cancel marker for user {}", calleeId)
    }

    fun popCancelMarker(userId: String): String? {
        val key = "$CANCEL_MARKER_KEY_PREFIX$userId"
        val payload = redisTemplate.opsForValue().getAndDelete(key)
        if (payload != null) {
            log.info("Popped cancel marker for user {}", userId)
        }
        return payload
    }

    /** Admin 诊断用：非破坏性读取 cancel marker，不消费 */
    fun peekCancelMarker(userId: String): String? {
        val key = "$CANCEL_MARKER_KEY_PREFIX$userId"
        return redisTemplate.opsForValue().get(key)
    }

    /** Admin 诊断用：查询 session key 的剩余 TTL（秒），-2 表示 key 不存在 */
    fun sessionTtlSeconds(roomId: Int): Long =
        redisTemplate.getExpire(sessionKey(roomId), TimeUnit.SECONDS) ?: -2L

    /** Admin 诊断用：查询 pending call 的剩余 TTL（秒），-2 表示 key 不存在 */
    fun pendingCallTtlSeconds(userId: String): Long =
        redisTemplate.getExpire("$PENDING_CALL_KEY_PREFIX$userId", TimeUnit.SECONDS) ?: -2L

    /** Admin 诊断用：查询 cancel marker 的剩余 TTL（秒），-2 表示 key 不存在 */
    fun cancelMarkerTtlSeconds(userId: String): Long =
        redisTemplate.getExpire("$CANCEL_MARKER_KEY_PREFIX$userId", TimeUnit.SECONDS) ?: -2L

    // ─── Session lifecycle ──────────────────────────────────────────────────

    /**
     * End a call session and create a chat message record.
     * @param reason: "completed" | "rejected" | "cancelled" | "busy" | "timeout" | "disconnected"
     * @param sourceChannel: 触发挂断的 WebSocket channel，传入后 conversation_updated 不会推送回同一 session
     */
    fun endSession(roomId: Int, reason: String, sourceChannel: io.netty.channel.Channel? = null) {
        val session = removeSession(roomId) ?: return
        cancelRingingTimeout(roomId)
        businessMetrics.decrementCallActive()
        if (reason == "timeout" || reason == "disconnected") {
            businessMetrics.callFailed.increment()
        }
        log.info("Call session ended: roomId={} reason={}", roomId, reason)
        audit.log("session_ended", roomId, session.callerId, mapOf(
            "calleeId" to session.calleeId,
            "reason" to reason,
            "answered" to (session.answeredAt != null),
        ))

        // 通过反向索引检查 callee 是否还有其他活跃通话
        val otherRoomId = redisTemplate.opsForValue().get(userCallKey(session.calleeId))
        if (otherRoomId == null) {
            clearPendingCall(session.calleeId)
        }

        val friendship = friendshipRepository.findByUserIdAndFriendId(session.callerId, session.calleeId)
        val conversationId = friendship?.conversationId
        if (conversationId == null) {
            log.warn("No conversation found for call record: caller={} callee={}", session.callerId, session.calleeId)
            return
        }

        val durationSeconds = if (session.answeredAt != null) {
            ((System.currentTimeMillis() - session.answeredAt) / 1000).toInt()
        } else {
            0
        }
        if (durationSeconds > 0) {
            businessMetrics.callDurationSeconds.increment(durationSeconds.toDouble())
        }

        val callTypeName = if (session.callType == 1) "视频通话" else "语音通话"
        val content = objectMapper.writeValueAsString(mapOf(
            "callType" to session.callType,
            "duration" to durationSeconds,
            "reason" to reason,
            "callerId" to session.callerId,
            "calleeId" to session.calleeId,
        ))

        try {
            messageService.sendMessage(
                senderId = session.callerId,
                conversationId = conversationId,
                content = content,
                contentType = 5,
                sourceChannel = sourceChannel,
            )
            log.info("Call record message sent: {} {}s reason={}", callTypeName, durationSeconds, reason)
        } catch (e: Exception) {
            log.error("Failed to send call record message: {}", e.message)
        }
    }

    /**
     * 服务端拦截忙线时调用：无 session 但仍需生成通话记录。
     */
    fun sendBusyCallRecord(callerId: String, calleeId: String, callType: Int) {
        val friendship = friendshipRepository.findByUserIdAndFriendId(callerId, calleeId)
        val conversationId = friendship?.conversationId
        if (conversationId == null) {
            log.warn("No conversation found for busy call record: caller={} callee={}", callerId, calleeId)
            return
        }
        val content = objectMapper.writeValueAsString(mapOf(
            "callType" to callType,
            "duration" to 0,
            "reason" to "busy",
            "callerId" to callerId,
            "calleeId" to calleeId,
        ))
        try {
            messageService.sendMessage(
                senderId = callerId,
                conversationId = conversationId,
                content = content,
                contentType = 5,
            )
            log.info("Busy call record sent: caller={} callee={}", callerId, calleeId)
        } catch (e: Exception) {
            log.error("Failed to send busy call record: {}", e.message)
        }
    }

    /**
     * 通过 Redis Set 获取所有活跃通话会话（供管理后台使用）。
     * 使用 SMEMBERS 替代 SCAN，兼容 ElastiCache Serverless (Valkey) 集群模式。
     * 逐个 GET 取值：MGET 在 Redis Cluster 下会因 key 跨 slot 抛 CROSSSLOT。
     */
    fun getActiveCallSessions(): List<CallSession> {
        val roomIds = redisTemplate.opsForSet().members(ACTIVE_SESSIONS_SET) ?: return emptyList()
        if (roomIds.isEmpty()) return emptyList()

        val roomIdList = roomIds.toList()
        val values = roomIdList.map { redisTemplate.opsForValue().get("$SESSION_KEY_PREFIX$it") }

        // 清理已过期但仍留在 Set 中的 roomId
        val expiredRoomIds = mutableListOf<String>()
        val sessions = values.mapIndexedNotNull { index, json ->
            if (json == null) {
                expiredRoomIds.add(roomIdList[index])
                return@mapIndexedNotNull null
            }
            try {
                objectMapper.readValue(json, CallSession::class.java)
            } catch (_: Exception) {
                expiredRoomIds.add(roomIdList[index])
                null
            }
        }
        if (expiredRoomIds.isNotEmpty()) {
            redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_SET, *expiredRoomIds.toTypedArray())
        }
        return sessions
    }

    fun endSessionByUser(userId: String, roomId: Int?, reason: String, sourceChannel: io.netty.channel.Channel? = null) {
        val rid = roomId ?: run {
            val roomIdStr = redisTemplate.opsForValue().get(userCallKey(userId)) ?: return
            roomIdStr.toIntOrNull() ?: return
        }
        endSession(rid, reason, sourceChannel)
    }
}
