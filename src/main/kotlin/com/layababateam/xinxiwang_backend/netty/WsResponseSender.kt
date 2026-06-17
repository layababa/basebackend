package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.ChannelHandlerContext

interface WsResponseSender {
    fun send(ctx: ChannelHandlerContext, data: Map<String, Any?>)
}
