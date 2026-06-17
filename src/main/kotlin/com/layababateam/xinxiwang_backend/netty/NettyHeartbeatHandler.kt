package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AttributeKey
import org.slf4j.LoggerFactory

class NettyHeartbeatHandler : ChannelInboundHandlerAdapter() {

    private val log = LoggerFactory.getLogger(NettyHeartbeatHandler::class.java)

    companion object {
        private val MISS_COUNT_KEY = AttributeKey.valueOf<Int>("heartbeat_miss")
        private const val MAX_MISS = 2
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            val misses = ctx.channel().attr(MISS_COUNT_KEY).get() ?: 0
            if (misses >= MAX_MISS) {
                log.info("Heartbeat miss limit reached ({}/{}), closing {}", misses, MAX_MISS, ctx.channel().remoteAddress())
                ctx.close()
                return
            }
            ctx.channel().attr(MISS_COUNT_KEY).set(misses + 1)
            ctx.channel().writeAndFlush(PingWebSocketFrame())
            log.debug("Heartbeat ping sent to {} (miss {}/{})", ctx.channel().remoteAddress(), misses + 1, MAX_MISS)
        } else {
            super.userEventTriggered(ctx, evt)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is PongWebSocketFrame) {
            ctx.channel().attr(MISS_COUNT_KEY).set(0)
            log.debug("Pong received from {}, miss counter reset", ctx.channel().remoteAddress())
            msg.release()
            return
        }
        super.channelRead(ctx, msg)
    }
}
