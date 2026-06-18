package com.layababateam.xinxiwang_backend.service

interface ApnsPushConsumerPort {
    fun handleApnsPush(event: ApnsPushEvent)
}

data class ApnsPushEvent(
    val userId: String,
    val wsMessage: String,
    val onlineAuthTokens: Set<String>,
)
