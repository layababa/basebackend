package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.ChannelHandler

/** 业务 WebSocket frame 处理器标记接口，供 SDK 的 Netty pipeline 装配使用。 */
interface NettyWebSocketFrameHandler : ChannelHandler
