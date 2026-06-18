package com.layababateam.xinxiwang_backend.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Weigher
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Decrypted media payload cache, bounded by total payload bytes.
 *
 * A weighted cache avoids large videos evicting many small thumbnails by entry count alone.
 * Entries expire by write time so rotated media keys eventually drop from memory.
 */
@Service
class MediaProxyCacheService(
    @Value("\${xinxiwang.media.proxy.cache-ttl-minutes}") private val ttlMinutes: Long,
    @Value("\${xinxiwang.media.proxy.cache-max-bytes}") private val maxBytes: Long,
) {
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
        .maximumWeight(maxBytes)
        .weigher(Weigher<String, ByteArray> { _, value -> value.size })
        .build<String, ByteArray>()

    fun get(key: String): ByteArray? = cache.getIfPresent(key)

    fun put(key: String, value: ByteArray) {
        cache.put(key, value)
    }

    fun invalidate(key: String) {
        cache.invalidate(key)
    }
}
