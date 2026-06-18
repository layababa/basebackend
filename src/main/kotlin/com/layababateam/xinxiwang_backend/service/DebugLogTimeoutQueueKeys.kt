package com.layababateam.xinxiwang_backend.service

object DebugLogTimeoutQueueKeys {
    const val TIMEOUT_ZSET_KEY = "debug_log_timeout"
    const val SHARD_COUNT = 16

    fun shardKey(reportId: String): String {
        val shard = (reportId.hashCode().toUInt() % SHARD_COUNT.toUInt()).toInt()
        return "$TIMEOUT_ZSET_KEY:$shard"
    }
}
