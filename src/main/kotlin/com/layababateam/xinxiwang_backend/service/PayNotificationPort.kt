package com.layababateam.xinxiwang_backend.service

interface PayNotificationPort {
    fun sendPayNotificationCard(
        userId: String,
        notifType: String,
        title: String,
        amount: String?,
        detail: String,
        address: String?,
        txHash: String?
    )
}
