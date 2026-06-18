package com.layababateam.xinxiwang_backend.service

interface ProfileUpdateConsumerPort {
    fun handleProfileUpdate(event: ProfileUpdateEvent)
}

data class ProfileUpdateEvent(
    val userId: String,
)
