package com.layababateam.xinxiwang_backend.service

interface AuthTokenResolver {
    fun resolveUserId(authHeader: String?): String?
}
