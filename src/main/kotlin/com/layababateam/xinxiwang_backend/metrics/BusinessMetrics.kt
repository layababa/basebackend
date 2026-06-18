package com.layababateam.xinxiwang_backend.metrics

import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class BusinessMetrics(
    private val registry: MeterRegistry,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository
) {

    val userRegistered: Counter = Counter.builder("business.user.registered")
        .description("Total user registrations")
        .register(registry)

    val userLogin: Counter = Counter.builder("business.user.login")
        .description("Total user logins")
        .register(registry)

    val messagesPersisted: Counter = Counter.builder("business.messages.persisted")
        .description("Total messages persisted to database")
        .register(registry)

    val groupSignalPushTotal: Counter = Counter.builder("group_signal_push_total")
        .description("Total group_message_signal pushes")
        .register(registry)

    val groupFullPushTotal: Counter = Counter.builder("group_full_push_total")
        .description("Total full new_message pushes for group messages")
        .register(registry)

    val groupSignalCoalescedTotal: Counter = Counter.builder("group_signal_coalesced_total")
        .description("Total group_message_signal entries coalesced")
        .register(registry)

    val groupSignalPushBytes: Counter = Counter.builder("group_signal_push_bytes")
        .description("Total bytes pushed as group_message_signal")
        .register(registry)

    val groupFullPushBytes: Counter = Counter.builder("group_full_push_bytes")
        .description("Total bytes pushed as full group new_message")
        .register(registry)

    val v3SyncQps: Counter = Counter.builder("v3_sync_qps")
        .description("Total v3 group sync requests")
        .register(registry)

    val v3SyncErrorTotal: Counter = Counter.builder("v3_sync_error_total")
        .description("Total v3 group sync errors")
        .register(registry)

    val v3SyncLimitedTotal: Counter = Counter.builder("v3_sync_limited_total")
        .description("Total v3 group sync requests limited")
        .register(registry)

    val v3SyncMessagesReturnedTotal: Counter = Counter.builder("v3_sync_messages_returned_total")
        .description("Total messages returned by v3 group sync")
        .register(registry)

    val v3SyncEmptyTotal: Counter = Counter.builder("v3_sync_empty_total")
        .description("Total empty v3 group sync responses")
        .register(registry)

    val v3SyncLatency: Timer = Timer.builder("v3_sync_latency")
        .description("Latency of v3 group sync")
        .publishPercentiles(0.95, 0.99)
        .register(registry)

    // ─── APNs Push ──────────────────────────────────────────────────────
    val apnsPushSuccess: Counter = Counter.builder("business.apns.push.success")
        .description("Total successful APNs push notifications")
        .register(registry)

    val apnsPushFailure: Counter = Counter.builder("business.apns.push.failure")
        .description("Total failed APNs push notifications")
        .register(registry)

    // ─── Calls ──────────────────────────────────────────────────────────
    val callInitiated: Counter = Counter.builder("business.call.initiated")
        .description("Total calls initiated")
        .register(registry)

    val callAnswered: Counter = Counter.builder("business.call.answered")
        .description("Total calls answered")
        .register(registry)

    val callFailed: Counter = Counter.builder("business.call.failed")
        .description("Total calls failed or timed out")
        .register(registry)

    val callDurationSeconds: Counter = Counter.builder("business.call.duration.seconds")
        .description("Total call duration in seconds (cumulative)")
        .register(registry)

    private val callActiveGauge = AtomicLong(0)

    // ─── Social ─────────────────────────────────────────────────────────
    val friendRequest: Counter = Counter.builder("business.friend.request")
        .description("Total friend requests sent")
        .register(registry)

    val momentPublished: Counter = Counter.builder("business.moment.published")
        .description("Total moments published")
        .register(registry)

    // ─── Red Packet ─────────────────────────────────────────────────────
    val redpacketSent: Counter = Counter.builder("business.redpacket.sent")
        .description("Total red packets sent")
        .register(registry)

    val redpacketClaimed: Counter = Counter.builder("business.redpacket.claimed")
        .description("Total red packets claimed")
        .register(registry)

    // ─── Traffic (HTTP + WS bytes，nginx 月度配额监控) ──────────────────
    // 通过 nginx 的字节量监控：分 OSS 中转 vs 非 OSS 业务流量。
    // 不含 backend 出站到 Mongo / Redis / RabbitMQ / OSS / APNs 内网链路。
    private val httpBytesCache = ConcurrentHashMap<String, Counter>()
    private val wsBytesCache = ConcurrentHashMap<String, Counter>()

    /**
     * @param direction "in" (request body) or "out" (response body)
     * @param group     oss-upload / oss-download / api / admin / other
     */
    fun recordHttpBytes(direction: String, group: String, bytes: Long) {
        if (bytes <= 0) return
        val key = "$direction:$group"
        httpBytesCache.computeIfAbsent(key) {
            Counter.builder("business.http.bytes")
                .description("HTTP body bytes through nginx, split by direction and group")
                .tag("direction", direction)
                .tag("group", group)
                .register(registry)
        }.increment(bytes.toDouble())
    }

    /**
     * @param direction "in" or "out"
     * @param kind      "heartbeat" or "business"
     */
    fun recordWsBytes(direction: String, kind: String, bytes: Long) {
        if (bytes <= 0) return
        val key = "$direction:$kind"
        wsBytesCache.computeIfAbsent(key) {
            Counter.builder("business.ws.bytes")
                .description("WebSocket frame payload bytes, split by direction and kind")
                .tag("direction", direction)
                .tag("kind", kind)
                .register(registry)
        }.increment(bytes.toDouble())
    }

    // ─── Gauges ─────────────────────────────────────────────────────────
    private val totalUsersGauge = AtomicLong(0)
    private val totalConversationsGauge = AtomicLong(0)
    private val totalGroupsGauge = AtomicLong(0)

    init {
        Gauge.builder("business.users.total", totalUsersGauge) { it.toDouble() }
            .description("Total registered users in database")
            .register(registry)

        Gauge.builder("business.call.active", callActiveGauge) { it.toDouble() }
            .description("Currently active calls")
            .register(registry)

        Gauge.builder("business.conversations.total", totalConversationsGauge) { it.toDouble() }
            .description("Total conversations in database")
            .register(registry)

        Gauge.builder("business.groups.total", totalGroupsGauge) { it.toDouble() }
            .description("Total group conversations in database")
            .register(registry)
    }

    fun incrementCallActive() { callActiveGauge.incrementAndGet() }
    fun decrementCallActive() { callActiveGauge.decrementAndGet() }

    @Scheduled(fixedRate = 60_000, initialDelay = 5_000)
    fun refreshTotalUsers() {
        try {
            totalUsersGauge.set(userRepository.count())
            totalConversationsGauge.set(conversationRepository.count())
            totalGroupsGauge.set(conversationRepository.countByType(1))
        } catch (_: Exception) {}
    }
}
