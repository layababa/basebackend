package com.layababateam.xinxiwang_backend.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class LoginSecurityMetrics(
    private val registry: MeterRegistry
) {
    private val counters = ConcurrentHashMap<String, Counter>()

    fun record(eventType: String, riskLevel: String, blocked: Boolean) {
        val safeType = eventType.take(40)
        val safeRisk = riskLevel.take(20)
        val key = "$safeType:$safeRisk:$blocked"
        counters.computeIfAbsent(key) {
            Counter.builder("security.login.events")
                .description("Client login security events")
                .tag("eventType", safeType)
                .tag("riskLevel", safeRisk)
                .tag("blocked", blocked.toString())
                .register(registry)
        }.increment()
    }
}
