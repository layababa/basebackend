package com.layababateam.xinxiwang_backend.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class WebSocketMetrics(
    private val registry: MeterRegistry,
) {
    private val activeConnections = AtomicInteger(0)
    private val typedReceivedCounters = ConcurrentHashMap<String, Counter>()
    private val typedHandlerTimers = ConcurrentHashMap<String, Timer>()
    private val versionCounters = ConcurrentHashMap<String, AtomicInteger>()

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
