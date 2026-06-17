package com.layababateam.xinxiwang_backend.service

interface PendingCallPort {
    fun peekPendingCall(userId: String): String?

    fun clearPendingCall(userId: String)

    fun hasActiveCallSession(roomId: Int): Boolean
}
