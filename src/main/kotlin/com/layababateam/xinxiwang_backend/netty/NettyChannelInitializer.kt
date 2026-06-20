package com.layababateam.xinxiwang_backend.netty

import com.layababateam.xinxiwang_backend.handler.ConnectionRateLimitHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.handler.timeout.IdleStateHandler
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class NettyChannelInitializer(
    private val webSocketHandler: NettyWebSocketFrameHandler,
    private val connectionRateLimitHandler: ConnectionRateLimitHandler,
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        ch.pipeline().apply {
            addLast(connectionRateLimitHandler)
            addLast(HttpServerCodec())
            addLast(ChunkedWriteHandler())
            addLast(HttpObjectAggregator(262144))
            // readerIdle（第1参数）：只看上行/入站流量，无上行=死/半开连接信号。
            // 改前 allIdle（第3参数）会被服务端下行写喂活，死连接永不 idle、永不回收。
            addLast(IdleStateHandler(45, 0, 0, TimeUnit.SECONDS))
            addLast(WebSocketPathRouter())
            addLast(
                WebSocketServerProtocolHandler(
                    WebSocketServerProtocolConfig.newBuilder()
                        .websocketPath("/websocket")
                        .maxFramePayloadLength(4 * 1024 * 1024)
                        // 放行入站 Pong，使其透传到 NettyHeartbeatHandler.channelRead 真正重置 miss。
                        // dropPongFrames 默认 true 会在到达心跳处理器前丢弃 Pong → MAX_MISS 容错实为 0、
                        // 健康静默连接会被误杀；此项是 readerIdle 改造正确性的前置条件。
                        .dropPongFrames(false)
                        .build()
                )
            )
            addLast(NettyHeartbeatHandler())
            addLast(webSocketHandler)
        }
    }
}
