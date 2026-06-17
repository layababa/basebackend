package com.layababateam.xinxiwang_backend.service

interface UserCacheInvalidationPort {
    fun invalidate(userId: String)
}
