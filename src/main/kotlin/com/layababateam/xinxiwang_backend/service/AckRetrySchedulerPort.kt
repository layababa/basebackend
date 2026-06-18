package com.layababateam.xinxiwang_backend.service

interface AckRetrySchedulerPort {
    fun scanPendingAcks()

    fun cleanupRetryKeysWithoutTtl()
}
