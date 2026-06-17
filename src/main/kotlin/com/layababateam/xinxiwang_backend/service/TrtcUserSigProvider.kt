package com.layababateam.xinxiwang_backend.service

interface TrtcUserSigProvider {
    val sdkAppId: Long

    fun genUserSig(userId: String): String
}
