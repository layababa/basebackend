package com.layababateam.xinxiwang_backend.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Component
class WebSocketMetrics(
    private val registry: MeterRegistry,
) {
    private val activeConnections = AtomicInteger(0)
    private val typedReceivedCounters = ConcurrentHashMap<String, Counter>()
    private val typedHandlerTimers = ConcurrentHashMap<String, Timer>()
    private val versionCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val boundOnlineUsersGauge = AtomicBoolean(false)

    val connectionsGauge: AtomicInteger =
        registry.gauge("ws.connections.active", activeConnections) ?: activeConnections

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

    fun getActiveConnectionCount(): Int = activeConnections.get()

    /**
     * 绑定去重在线用户数 gauge（ws_online_users）。
     *
     * `ws_connections_active` 计的是 channel 数（含多端/多重连），无法反映真实在线人数。
     * 接入方在持有去重在线用户注册表（如 `userChannels`，key 为 userId）时，于 `@PostConstruct`
     * 传入 `{ userChannels.size }` 即可让本指标反映去重后的在线人数。
     *
     * 用 supplier 回调反转依赖方向，避免接入方的会话管理器与本类形成构造循环。
     * 幂等：重复调用仅首次生效。
     */
    fun bindOnlineUsersGauge(supplier: () -> Number) {
        if (!boundOnlineUsersGauge.compareAndSet(false, true)) return
        Gauge.builder("ws.online.users", supplier) { it().toDouble() }
            .description("Distinct online users on this node (deduplicated by userId)")
            .register(registry)
    }

    fun connectionOpened(platform: String? = null, version: String? = null) {
        activeConnections.incrementAndGet()
        knownClientVersion(platform, version)?.let { (knownPlatform, knownVersion) ->
            getOrCreateVersionGauge(knownPlatform, knownVersion).incrementAndGet()
        }
    }

    fun connectionClosed(platform: String? = null, version: String? = null) {
        activeConnections.decrementAndGet()
        knownClientVersion(platform, version)?.let { (knownPlatform, knownVersion) ->
            versionCounters[versionKey(knownPlatform, knownVersion)]?.decrementAndGet()
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
        }.record(Duration.ofNanos(durationNanos))
    }

    private fun getOrCreateVersionGauge(platform: String, version: String): AtomicInteger {
        val key = versionKey(platform, version)
        return versionCounters.computeIfAbsent(key) {
            val gauge = AtomicInteger(0)
            registry.gauge(
                "ws.connections.version",
                Tags.of("platform", platform, "version", version),
                gauge,
            )
            gauge
        }
    }

    private fun knownClientVersion(platform: String?, version: String?): Pair<String, String>? {
        if (platform.isNullOrBlank() || version.isNullOrBlank()) return null
        if (platform == UNKNOWN_VALUE || version == UNKNOWN_VALUE) return null
        return platform to version
    }

    private fun versionKey(platform: String, version: String): String = "$platform:$version"

    private companion object {
        const val UNKNOWN_VALUE = "unknown"
    }
}
