package com.layababateam.xinxiwang_backend.service

interface OfficialNotificationSender {
    fun sendOfficialMessage(userId: String, content: String)
}
