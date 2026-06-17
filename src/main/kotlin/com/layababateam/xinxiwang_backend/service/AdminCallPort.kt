package com.layababateam.xinxiwang_backend.service

interface AdminCallPort {
    fun getCallByUser(userId: String): Map<String, Any?>

    fun getCallByRoom(roomId: Int): Map<String, Any?>?

    fun getPendingCallState(userId: String): Map<String, Any?>

    fun forceEndCall(roomId: Int, reason: String): Map<String, Any?>?

    fun getCallHistory(userId: String, page: Int, size: Int): Map<String, Any?>
}
