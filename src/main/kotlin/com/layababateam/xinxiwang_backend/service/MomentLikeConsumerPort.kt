package com.layababateam.xinxiwang_backend.service

interface MomentLikeConsumerPort {
    fun persistMomentLike(event: MomentLikeEvent)
}

data class MomentLikeEvent(
    val action: String,
    val momentId: String,
    val userId: String,
)
