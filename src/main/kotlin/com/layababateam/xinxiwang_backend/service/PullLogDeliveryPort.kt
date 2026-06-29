package com.layababateam.xinxiwang_backend.service

import io.netty.channel.Channel

interface PullLogDeliveryPort {
    fun getChannels(userId: String): Set<Channel>

    fun getTokenForChannel(channel: Channel): String?

    fun sendJsonToChannel(channel: Channel, message: String)
}
