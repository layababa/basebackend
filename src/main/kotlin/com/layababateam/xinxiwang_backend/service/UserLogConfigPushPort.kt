package com.layababateam.xinxiwang_backend.service

interface UserLogConfigPushPort {
    fun pushClientLogConfigToEligibleUser(userId: String, message: String): Int

    fun getEligibleClientLogDeviceIds(userId: String): Set<String>
}
