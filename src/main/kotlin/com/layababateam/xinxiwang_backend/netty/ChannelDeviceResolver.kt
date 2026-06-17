package com.layababateam.xinxiwang_backend.netty

import io.netty.channel.Channel

interface ChannelDeviceResolver {
    fun getDeviceId(channel: Channel): String?
}
