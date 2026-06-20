package com.layababateam.xinxiwang_backend.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

/** `ws_online_users` 去重在线人数 gauge（根因③后端）契约测试。 */
class WebSocketMetricsOnlineUsersTest {

    /** 用例A：gauge 值反映 supplier 返回值。 */
    @Test
    fun `gauge reflects supplier value`() {
        val registry = SimpleMeterRegistry()
        val metrics = WebSocketMetrics(registry)

        metrics.bindOnlineUsersGauge { 7 }

        assertEquals(7.0, registry.get("ws.online.users").gauge().value())
    }

    /** 用例B：幂等 —— 重复绑定只首次生效，registry 中仅 1 个 meter，取首次 supplier。 */
    @Test
    fun `bind is idempotent and keeps first supplier`() {
        val registry = SimpleMeterRegistry()
        val metrics = WebSocketMetrics(registry)

        metrics.bindOnlineUsersGauge { 3 }
        metrics.bindOnlineUsersGauge { 99 }

        val gauges = registry.meters.filter { it.id.name == "ws.online.users" }
        assertEquals(1, gauges.size, "重复绑定不得注册第二个 meter")
        assertEquals(3.0, registry.get("ws.online.users").gauge().value(), "应保留首次 supplier")
    }

    /** 用例C：live 绑定 —— supplier 引用可变状态，状态变化后 gauge 跟随（非快照）。 */
    @Test
    fun `gauge is live-bound to mutable state`() {
        val registry = SimpleMeterRegistry()
        val metrics = WebSocketMetrics(registry)
        var online = 0
        metrics.bindOnlineUsersGauge { online }

        assertEquals(0.0, registry.get("ws.online.users").gauge().value())
        online = 42
        assertEquals(42.0, registry.get("ws.online.users").gauge().value())
    }
}
