package com.layababateam.xinxiwang_backend.service

interface PayNotificationPort {
    fun sendPayNotification(
        userId: String,
        notifType: String,
        title: String,
        amount: String? = null,
        detail: String,
        address: String? = null,
        txHash: String? = null
    )
}
