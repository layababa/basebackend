package com.layababateam.xinxiwang_backend.service

interface MediaKeyBroadcastPort {
    fun broadcastMediaKeyPayload(payload: String): Int
}
