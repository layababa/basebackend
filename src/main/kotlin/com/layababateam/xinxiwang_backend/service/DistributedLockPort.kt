package com.layababateam.xinxiwang_backend.service

interface DistributedLockPort {
    fun <T> withLock(key: String, action: () -> T): T
}
