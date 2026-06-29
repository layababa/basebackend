package com.layababateam.xinxiwang_backend.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Component
class WebSocketMetrics(private val registry: MeterRegistry) {

    private val activeConnections = AtomicInteger(0)
    private val typedReceivedCounters = ConcurrentHashMap<String, Counter>()
    private val typedHandlerTimers = ConcurrentHashMap<String, Timer>()
    private val typedQueueDelayTimers = ConcurrentHashMap<String, Timer>()
    private val typedRateLimitedCounters = ConcurrentHashMap<String, Counter>()
    private val boundExecutors = ConcurrentHashMap.newKeySet<String>()

    val connectionsGauge = registry.gauge("ws.connections.active", activeConnections) ?: activeConnections

    private val messagesSentCounter = Counter.builder("ws.messages.sent")
        .description("Total WebSocket messages sent to clients")
        .register(registry)

    private val authSuccessCounter = Counter.builder("ws.auth")
        .tag("result", "success")
        .description("WebSocket authentication successes")
        .register(registry)

    private val authFailureCounter = Counter.builder("ws.auth")
        .tag("result", "failure")
        .description("WebSocket authentication failures")
        .register(registry)

    // ── 版本分佈 Gauge ──────────────────────────────────────────
    private val versionCounters = ConcurrentHashMap<String, AtomicInteger>()

    private fun versionKey(platform: String, version: String) = "$platform:$version"

    private fun getOrCreateVersionGauge(platform: String, version: String): AtomicInteger {
        val key = versionKey(platform, version)
        return versionCounters.computeIfAbsent(key) { k ->
            val gauge = AtomicInteger(0)
            registry.gauge(
                "ws.connections.version",
                Tags.of("platform", platform, "version", version),
                gauge
            )
            gauge
        }
    }

    // ── 去重在线人数 Gauge（ws_online_users）──────────────────────
    private val boundOnlineUsersGauge = AtomicBoolean(false)
    private var onlineUsersSupplier: (() -> Number)? = null

    /**
     * 绑定去重在线用户数 gauge（ws_online_users）。语义 = 当前节点 userChannels.size（按 userId 去重，
     * 天然去重多设备/多重连）。幂等：重复调用仅首次生效。supplier 由 UserSessionManager 通过
     * @PostConstruct 传入 `{ userChannels.size }`，反转依赖方向避免循环。
     */
    fun bindOnlineUsersGauge(supplier: () -> Number) {
        if (!boundOnlineUsersGauge.compareAndSet(false, true)) return
        onlineUsersSupplier = supplier
        Gauge.builder("ws.online.users", supplier) { it().toDouble() }
            .description("Distinct online users on this node (deduplicated by userId)")
            .register(registry)
    }

    fun getActiveConnectionCount(): Int = activeConnections.get()

    fun connectionOpened(platform: String? = null, version: String? = null) {
        activeConnections.incrementAndGet()
        if (!platform.isNullOrBlank() && !version.isNullOrBlank() && platform != "unknown" && version != "unknown") {
            getOrCreateVersionGauge(platform, version).incrementAndGet()
        }
    }

    fun connectionClosed(platform: String? = null, version: String? = null) {
        activeConnections.decrementAndGet()
        if (!platform.isNullOrBlank() && !version.isNullOrBlank() && platform != "unknown" && version != "unknown") {
            val key = versionKey(platform, version)
            versionCounters[key]?.decrementAndGet()
        }
    }

    fun messageReceived(type: String) {
        typedReceivedCounters.computeIfAbsent(type) {
            Counter.builder("ws.messages.received.typed")
                .tag("type", type)
                .description("WebSocket messages received by type")
                .register(registry)
        }.increment()
    }

    fun messageSent() {
        messagesSentCounter.increment()
    }

    fun requestRateLimited(type: String) {
        typedRateLimitedCounters.computeIfAbsent(type) {
            Counter.builder("ws.requests.ratelimited")
                .tag("type", type)
                .description("WebSocket requests rate-limited by backend guard")
                .register(registry)
        }.increment()
    }

    fun authSuccess() {
        authSuccessCounter.increment()
    }

    fun authFailure() {
        authFailureCounter.increment()
    }

    fun recordHandlerDuration(type: String, durationNanos: Long) {
        typedHandlerTimers.computeIfAbsent(type) {
            Timer.builder("ws.handler.duration")
                .tag("type", type)
                .description("Handler processing duration by type")
                .register(registry)
        }.record(java.time.Duration.ofNanos(durationNanos))
    }

    fun recordTaskQueueDelay(executor: String, type: String, delayNanos: Long) {
        typedQueueDelayTimers.computeIfAbsent("$executor:$type") {
            Timer.builder("ws.task.queue.delay")
                .tags("executor", executor, "type", type)
                .description("Time spent waiting in the WebSocket business executor queue")
                .register(registry)
        }.record(java.time.Duration.ofNanos(delayNanos))
    }

    fun bindExecutor(name: String, executor: ThreadPoolExecutor) {
        if (!boundExecutors.add(name)) return
        Gauge.builder("ws.executor.active", executor) { it.activeCount.toDouble() }
            .tags(Tags.of("executor", name))
            .description("Active WebSocket executor threads")
            .register(registry)
        Gauge.builder("ws.executor.pool.size", executor) { it.poolSize.toDouble() }
            .tags(Tags.of("executor", name))
            .description("Current WebSocket executor pool size")
            .register(registry)
        Gauge.builder("ws.executor.queue.size", executor) { it.queue.size.toDouble() }
            .tags(Tags.of("executor", name))
            .description("Queued WebSocket executor tasks")
            .register(registry)
        Gauge.builder("ws.executor.completed", executor) { it.completedTaskCount.toDouble() }
            .tags(Tags.of("executor", name))
            .description("Completed WebSocket executor tasks")
            .register(registry)
    }
}
