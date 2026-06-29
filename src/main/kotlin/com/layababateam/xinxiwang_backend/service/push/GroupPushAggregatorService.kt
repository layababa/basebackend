package com.layababateam.xinxiwang_backend.service.push

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.UserConversationRepository
import com.layababateam.xinxiwang_backend.service.ConversationService
import com.layababateam.xinxiwang_backend.service.UserSessionManager
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 群聊推送聚合服务（debounce 5s + 上限 60s）
 *
 * - 第一条群消息到达 → 等 5 秒
 * - 5s 内有新消息 → 重置 5s 计时器，继续等
 * - 5s 内没新消息 → 立即推送 "你收到了 X 条新消息"
 * - 无论如何，从第一条消息起最多等 60s 必须推送
 *
 * 使用 Redis INCR 做跨节点计数，ScheduledExecutor + ConcurrentHashMap.compute 做本地 debounce。
 */
@Service
class GroupPushAggregatorService(
    private val redisTemplate: StringRedisTemplate,
    private val pushDispatchService: PushDispatchService,
    private val conversationService: ConversationService,
    private val conversationCacheService: ConversationCacheService,
    private val userConversationRepository: UserConversationRepository,
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val scheduler = Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "push-agg-${System.nanoTime()}").apply { isDaemon = true }
    }

    /** debounce 定时器：每次新消息重置 */
    private val debounceTimers = ConcurrentHashMap<String, ScheduledFuture<*>>()

    /** 硬上限定时器：第一条消息设定，到期必须 flush，不会被重置 */
    private val maxTimers = ConcurrentHashMap<String, ScheduledFuture<*>>()

    companion object {
        private const val DEBOUNCE_SECONDS = 5L
        private const val MAX_WINDOW_SECONDS = 60L
        private const val REDIS_PREFIX = "rentmsg:push:agg:"
    }

    /**
     * 尝试聚合一条群聊推送。
     *
     * @return true  已聚合（调用方不需要再推送）
     * @return false 不是群消息或不适用聚合（调用方应走原有推送逻辑）
     */
    @Suppress("UNCHECKED_CAST")
    fun tryAggregate(userId: String, wsMessage: String): Boolean {
        val json = try {
            objectMapper.readValue(wsMessage, Map::class.java)
        } catch (_: Exception) {
            return false
        }

        val type = json["type"] as? String ?: return false
        if (type != "new_message") return false

        val data = json["data"] as? Map<String, Any?> ?: return false
        val groupName = data["groupName"] as? String
        if (groupName.isNullOrBlank()) return false // 私��，不聚合

        val convId = data["conversationId"] as? String ?: return false

        // 免打扰直接吞掉
        val uc = userConversationRepository.findFirstByUserIdAndConversationId(userId, convId)
        if (uc?.muted == true) {
            log.debug("[推送聚合] 会话 {} 已被用户 {} 免打扰，跳过", convId, userId)
            return true
        }

        val counterKey = "${REDIS_PREFIX}count:${userId}:${convId}"
        val flushKey = "$userId:$convId"

        // 累加计数
        val count = redisTemplate.opsForValue().increment(counterKey) ?: 1
        redisTemplate.expire(counterKey, Duration.ofSeconds(MAX_WINDOW_SECONDS + 30))

        // 第一条消息：设定 60s 硬上限定时器（不会被后续消息重置）
        if (count == 1L) {
            log.info("[推送聚合] 新窗口: userId={}, convId={}", userId, convId)
            val maxFuture = scheduler.schedule({
                doFlush(userId, convId)
            }, MAX_WINDOW_SECONDS, TimeUnit.SECONDS)
            maxTimers[flushKey] = maxFuture
        }

        // 每条消息：重置 5s debounce 定时器（cancel 旧的 + 调度新的）
        debounceTimers.compute(flushKey) { _, existing ->
            existing?.cancel(false)
            scheduler.schedule({
                doFlush(userId, convId)
            }, DEBOUNCE_SECONDS, TimeUnit.SECONDS)
        }

        return true
    }

    /**
     * 执行 flush：推送聚合通知并清理所有状态。
     * 可能被 debounce 定时器或 max 定时器触发，用 getAndDelete 保证只执行一次。
     */
    private fun doFlush(userId: String, convId: String) {
        val counterKey = "${REDIS_PREFIX}count:${userId}:${convId}"
        val flushKey = "$userId:$convId"

        // 原子读取并删除计数，保证只有一个线程真正 flush
        val windowCount = redisTemplate.opsForValue().getAndDelete(counterKey)?.toLongOrNull() ?: 0

        // 清理两个定时器
        debounceTimers.remove(flushKey)?.cancel(false)
        maxTimers.remove(flushKey)?.cancel(false)

        if (windowCount <= 0) return

        try {
            flushAggregatedPush(userId, convId, windowCount)
        } catch (e: Exception) {
            log.error("[推送��合] flush 失败: userId={}, convId={}", userId, convId, e)
        }
    }

    private fun flushAggregatedPush(userId: String, convId: String, windowCount: Long) {
        // 用实际未读数（更准确）；如果查询失败则退化为窗口计数
        val unreadCount = try {
            conversationService.getUnreadCount(userId, convId)
        } catch (_: Exception) {
            windowCount
        }
        val displayCount = maxOf(unreadCount, windowCount).toInt()
        if (displayCount <= 0) return

        // 获取群信息
        val conv = conversationCacheService.getConversation(convId)
        val groupName = conv?.name ?: "群聊"
        val groupAvatar = conv?.avatarUrl

        // badge = 所有会话未读总数
        val badgeCount = try {
            conversationService.getTotalUnreadCount(userId).toInt().coerceAtLeast(1)
        } catch (_: Exception) { 1 }

        val title = groupName
        val body = "你收到了${displayCount}条新消息"
        val customData = mutableMapOf<String, Any>(
            "type" to "new_message",
            "conversationId" to convId
        )
        if (groupAvatar != null) customData["avatarUrl"] = groupAvatar

        log.info(
            "[推送聚合] 推送: userId={}, convId={}, 未��={}, 窗口={}, badge={}",
            userId, convId, unreadCount, windowCount, badgeCount
        )

        val onlineAuthTokens = userSessionManager.getOnlineAuthTokens(userId)
        pushDispatchService.pushAggregatedGroupNotification(
            userId, title, body, customData, badgeCount, convId, onlineAuthTokens
        )
    }

    @PreDestroy
    fun shutdown() {
        log.info("[推送聚合] 关闭, 取消 {} 个待发任务", debounceTimers.size + maxTimers.size)
        debounceTimers.values.forEach { it.cancel(false) }
        maxTimers.values.forEach { it.cancel(false) }
        scheduler.shutdown()
    }
}
