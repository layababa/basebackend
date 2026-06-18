package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.Channel

/**
 * 业务会话层的通道发送端口，SDK WebSocket 响应工具只依赖发送能力。
 */
interface ChannelMessageSender {
    fun sendToChannel(channel: Channel, message: String)

    fun sendJsonToChannel(channel: Channel, message: String)
}
