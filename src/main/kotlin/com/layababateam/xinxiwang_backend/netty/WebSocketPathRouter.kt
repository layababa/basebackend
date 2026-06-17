package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.util.AttributeKey
import org.slf4j.LoggerFactory

class WebSocketPathRouter : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {
            routeUpgradeRequest(ctx, msg)
        }
        ctx.fireChannelRead(msg)
    }

    private fun routeUpgradeRequest(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        val uri = request.uri()
        when {
            uri.startsWith(V3_PATH_PREFIX) -> {
                ctx.channel().attr(API_VERSION_KEY).set(3)
                request.setUri(uri.replaceFirst(V3_PATH_PREFIX, WEBSOCKET_PATH))
                log.debug("WebSocket v3 upgrade from {}", ctx.channel().remoteAddress())
            }

            uri.startsWith(WEBSOCKET_PATH) -> {
                ctx.channel().attr(API_VERSION_KEY).set(1)
            }
        }
    }

    companion object {
        val API_VERSION_KEY: AttributeKey<Int> = AttributeKey.valueOf("ws.api.version")

        private const val WEBSOCKET_PATH = "/websocket"
        private const val V3_PATH_PREFIX = "/websocket/v3"
        private val log = LoggerFactory.getLogger(WebSocketPathRouter::class.java)
    }
}
