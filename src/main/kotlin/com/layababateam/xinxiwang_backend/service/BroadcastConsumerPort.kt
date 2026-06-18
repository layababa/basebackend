package com.layababateam.xinxiwang_backend.service

interface BroadcastConsumerPort {
    fun handleBroadcast(event: BroadcastMessageEvent)
}

data class BroadcastMessageEvent(
    val message: String,
    val adminId: String,
    val broadcastId: String,
)
