package com.layababateam.xinxiwang_backend.service

interface ClientAuthRefreshPolicy {
    val refreshTokenTtlOnClientAuth: Boolean
        get() = true
}
