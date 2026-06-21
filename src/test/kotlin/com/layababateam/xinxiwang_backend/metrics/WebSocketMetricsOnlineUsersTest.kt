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

    /**
     * 用例D：supplier 无外部强引用时，GC 后 gauge 不得为 NaN（Micrometer 弱引用回归锁）。
     *
     * 关键：用【捕获变量】的 lambda。Kotlin 把无捕获 lambda（如 `{ 42 }`）编译成永不回收的单例，
     * 那样测不出弱引用缺陷 —— 必须让 lambda 捕获外部变量，才是真实可回收的实例。
     * 撤掉源码里「字段强引用 supplier」那行，本用例即转红（线上 ws_online_users=NaN 的根因）。
     */
    @Test
    fun `gauge survives GC when supplier has no external strong reference`() {
        val registry = SimpleMeterRegistry()
        val metrics = WebSocketMetrics(registry)

        val holder = java.util.concurrent.atomic.AtomicInteger(42)
        metrics.bindOnlineUsersGauge { holder.get() }

        repeat(5) { System.gc(); System.runFinalization(); Thread.sleep(20) }

        assertEquals(
            42.0, registry.get("ws.online.users").gauge().value(),
            "GC 后 gauge 不应为 NaN —— supplier 必须被 WebSocketMetrics 字段强引用住",
        )
    }
}
