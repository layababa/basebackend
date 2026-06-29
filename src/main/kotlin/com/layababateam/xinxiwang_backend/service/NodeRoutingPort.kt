package com.layababateam.xinxiwang_backend.service

import io.netty.channel.Channel

interface NodeRoutingPort {
    fun refreshClientLogEligibility(userId: String? = null)

    fun disconnectUserLocal(userId: String)

    fun findChannelByToken(userId: String, token: String): Channel?

    fun pushClientLogConfigToLocalEligibleUser(userId: String, message: String): Int

    fun getChannels(userId: String): Set<Channel>
}
