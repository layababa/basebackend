package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 心跳行为 **pipeline 级**集成测试（根因① + MF-1 回归锁）。
 *
 * 刻意装真实 `WebSocketServerProtocolHandler` + `NettyHeartbeatHandler` 并完成 WS 握手，
 * 复现生产帧路由 —— 不用「孤立 handler」测试（那会假绿灯掩盖 dropPongFrames 默认 true 时
 * 入站 Pong 被上游吞掉、心跳处理器的 Pong 重置成死代码的 bug）。
 */
class NettyHeartbeatHandlerReaderIdleTest {

    private fun handshakenPipeline(dropPongFrames: Boolean): EmbeddedChannel {
        val config = WebSocketServerProtocolConfig.newBuilder()
            .websocketPath("/websocket")
            .maxFramePayloadLength(4 * 1024 * 1024)
            .dropPongFrames(dropPongFrames)
            .build()
        val ch = EmbeddedChannel(
            HttpServerCodec(),
            HttpObjectAggregator(65536),
            WebSocketServerProtocolHandler(config),
            NettyHeartbeatHandler(),
        )
        val upgrade = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/websocket")
        upgrade.headers().apply {
            set(HttpHeaderNames.HOST, "localhost")
            set(HttpHeaderNames.CONNECTION, "Upgrade")
            set(HttpHeaderNames.UPGRADE, "websocket")
            set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13")
            set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
        }
        ch.writeInbound(upgrade)
        drainOutbound(ch) // 丢弃 101 握手响应 + 后续 ping 编码字节
        return ch
    }

    private fun drainOutbound(ch: EmbeddedChannel) {
        while (true) {
            val msg = ch.readOutbound<Any?>() ?: break
            ReferenceCountUtil.release(msg)
        }
    }

    private fun fireReaderIdle(ch: EmbeddedChannel) {
        ch.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT)
        drainOutbound(ch)
    }

    /** 用例A：死/半开连接（无任何入站）→ miss 累加到 2 → 第 3 次 idle 关闭。 */
    @Test
    fun `dead connection closed after MAX_MISS reader-idle events`() {
        val ch = handshakenPipeline(dropPongFrames = false)

        fireReaderIdle(ch) // miss 0→1
        fireReaderIdle(ch) // miss 1→2
        assertTrue(ch.isOpen, "miss 未达上限前不应关闭")

        fireReaderIdle(ch) // misses(2) >= MAX_MISS → close
        assertFalse(ch.isOpen, "死连接应在 MAX_MISS 后被关闭")
    }

    /**
     * 用例B（MF-1 回归锁）：dropPongFrames(false) 下，入站 Pong 必须透传到 NettyHeartbeatHandler
     * 并把 miss 清零 —— 重置后再 fire 2 次 idle 仍不应关闭。若回退到默认 true，本用例必须转红。
     */
    @Test
    fun `inbound pong passes through and resets miss when dropPongFrames is false`() {
        val ch = handshakenPipeline(dropPongFrames = false)

        fireReaderIdle(ch)                       // miss 0→1
        ch.writeInbound(PongWebSocketFrame())    // Pong 透传 → miss 清零
        fireReaderIdle(ch)                       // miss 0→1
        fireReaderIdle(ch)                       // miss 1→2

        assertTrue(ch.isOpen, "Pong 应已重置 miss，两次 idle 不足以关闭健康连接")
    }

    /** 用例C：只收不发但忠实回 Pong 的健康静默连接，循环 idle→Pong 全程不被误杀。 */
    @Test
    fun `healthy silent connection answering pong is never killed`() {
        val ch = handshakenPipeline(dropPongFrames = false)

        repeat(5) {
            fireReaderIdle(ch)
            ch.writeInbound(PongWebSocketFrame())
            assertTrue(ch.isOpen, "回 Pong 的健康连接不应被淘汰")
        }
    }

    /**
     * 用例D（dropPongFrames 反例）：默认 true 时入站 Pong 被 WS 协议处理器吞掉、到不了心跳处理器，
     * miss 不被重置 → 同样的 idle/Pong 序列下连接被误杀。文档化 MF-1 根因、防回归。
     */
    @Test
    fun `inbound pong is swallowed and connection killed when dropPongFrames is true`() {
        val ch = handshakenPipeline(dropPongFrames = true)

        fireReaderIdle(ch)                       // miss 0→1
        ch.writeInbound(PongWebSocketFrame())    // Pong 被 WSPH 丢弃，miss 未重置
        fireReaderIdle(ch)                       // miss 1→2
        fireReaderIdle(ch)                       // misses(2) >= MAX_MISS → close

        assertFalse(ch.isOpen, "dropPongFrames=true 时 Pong 被吞，健康连接被误杀（MF-1）")
    }
}
