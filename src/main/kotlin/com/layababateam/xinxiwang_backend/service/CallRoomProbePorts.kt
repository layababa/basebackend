package com.layababateam.xinxiwang_backend.service

data class CallRoomProbeSession(
    val roomId: Int,
    val callerId: String,
    val calleeId: String,
    val answered: Boolean,
)

interface CallRoomProbeSessionPort {
    fun getCallRoomProbeSession(roomId: Int): CallRoomProbeSession?
}

interface TrtcRoomUsersPort {
    fun activeRoomUsers(roomId: Int): List<String>?
}
