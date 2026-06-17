package com.layababateam.xinxiwang_backend.config

interface MediaKeySnapshotProvider {
    fun snapshotForClient(): Map<String, Any>
}
