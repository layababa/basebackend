package com.layababateam.xinxiwang_backend.service

interface GroupMessageDispatchConsumerPort {
    fun dispatchGroupMessage(payload: Map<String, Any?>)
}
