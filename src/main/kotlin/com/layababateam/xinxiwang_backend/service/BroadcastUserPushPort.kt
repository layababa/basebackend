package com.layababateam.xinxiwang_backend.service

interface BroadcastUserPushPort {
    fun pushToUser(userId: String, message: String, skipApns: Boolean): Boolean
}
