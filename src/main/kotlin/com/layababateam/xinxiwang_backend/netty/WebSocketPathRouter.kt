package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.util.AttributeKey
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class WebSocketPathRouter : ChannelInboundHandlerAdapter() {

    companion object {
        val API_VERSION_KEY: AttributeKey<Int> = AttributeKey.valueOf("ws.api.version")
        val CLIENT_IP_KEY: AttributeKey<String> = AttributeKey.valueOf("ws.client.ip")
    }

    private val log = LoggerFactory.getLogger(WebSocketPathRouter::class.java)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {
            ctx.channel().attr(CLIENT_IP_KEY).set(resolveClientIp(ctx, msg))
            val uri = msg.uri()
            when {
                uri.startsWith("/websocket/v3") -> {
                    ctx.channel().attr(API_VERSION_KEY).set(3)
                    msg.setUri(uri.replaceFirst("/websocket/v3", "/websocket"))
                    log.debug("WebSocket v3 upgrade from {}", ctx.channel().remoteAddress())
                }
                uri.startsWith("/websocket") -> {
                    ctx.channel().attr(API_VERSION_KEY).set(1)
                }
            }
        }
        ctx.fireChannelRead(msg)
    }

    private fun resolveClientIp(ctx: ChannelHandlerContext, request: FullHttpRequest): String {
        val forwardedFor = request.headers()
            .get("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        if (forwardedFor != null) return forwardedFor

        val realIp = request.headers()
            .get("X-Real-IP")
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        if (realIp != null) return realIp

        return (ctx.channel().remoteAddress() as? InetSocketAddress)
            ?.address
            ?.hostAddress
            ?: "unknown"
    }
}
