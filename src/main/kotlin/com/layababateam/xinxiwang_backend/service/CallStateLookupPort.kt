package com.layababateam.xinxiwang_backend.service

data class CallStateSnapshot(
    val inCall: Boolean,
    val roomId: Int?,
    val peerId: String?,
    val answered: Boolean,
)

interface CallStateLookupPort {
    fun getCallState(userId: String): CallStateSnapshot
}
