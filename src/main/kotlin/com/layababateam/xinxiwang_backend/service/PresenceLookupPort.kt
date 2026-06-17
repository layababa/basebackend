package com.layababateam.xinxiwang_backend.service

interface PresenceLookupPort {
    fun isOnlineGlobally(userId: String): Boolean
}
