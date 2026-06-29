package com.layababateam.xinxiwang_backend.service

interface BroadcastPublisherPort {
    fun publishBroadcast(queue: String, payload: Map<String, Any>, action: String)
}
