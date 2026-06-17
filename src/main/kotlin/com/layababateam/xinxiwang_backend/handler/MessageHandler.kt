package com.layababateam.xinxiwang_backend.handler

import io.netty.channel.ChannelHandlerContext

interface MessageHandler {
    val type: String
    fun handle(ctx: ChannelHandlerContext, userId: String, data: Map<String, Any?>)
}
