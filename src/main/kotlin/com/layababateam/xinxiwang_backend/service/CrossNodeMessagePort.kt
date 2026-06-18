package com.layababateam.xinxiwang_backend.service

interface CrossNodeMessagePort {
    fun handleCrossNodeMessage(payload: Map<String, Any?>)
}
