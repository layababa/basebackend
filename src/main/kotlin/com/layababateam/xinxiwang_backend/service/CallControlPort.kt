package com.layababateam.xinxiwang_backend.service

import io.netty.channel.Channel

data class CallAcceptResult(
    val accepted: Boolean,
    val rejectReason: String? = null,
)

interface CallControlPort {
    fun acceptCall(userId: String, targetUserId: String, roomId: Int?, deviceId: String?): CallAcceptResult

    fun rejectCall(userId: String, targetUserId: String, roomId: Int?, sourceChannel: Channel)

    fun cancelCall(userId: String, targetUserId: String, roomId: Int?, sourceChannel: Channel)

    fun hangupCall(
        userId: String,
        targetUserId: String,
        roomId: Int?,
        callType: Int,
        sourceChannel: Channel,
    )

    fun markBusy(userId: String, targetUserId: String, roomId: Int?, sourceChannel: Channel)
}
