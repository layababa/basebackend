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
            addLast(IdleStateHandler(0, 0, 45, TimeUnit.SECONDS))
            addLast(WebSocketPathRouter())
            addLast(
                WebSocketServerProtocolHandler(
                    WebSocketServerProtocolConfig.newBuilder()
                        .websocketPath("/websocket")
                        .maxFramePayloadLength(4 * 1024 * 1024)
                        .build()
                )
            )
            addLast(NettyHeartbeatHandler())
            addLast(webSocketHandler)
        }
    }
}
