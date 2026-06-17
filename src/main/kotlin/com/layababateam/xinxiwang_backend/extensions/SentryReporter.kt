package com.layababateam.xinxiwang_backend.extensions

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object SentryReporter {
    private const val DEDUP_WINDOW_MS = 10 * 60 * 1000L
    private val dedupWindowDuration: Duration = Duration.ofMillis(DEDUP_WINDOW_MS)
    private val lastSeen = ConcurrentHashMap<String, Long>()

    @Volatile
    internal var redisTemplate: StringRedisTemplate? = null

    fun captureSampled(
        dedupKey: String,
        message: String,
        level: SentryLevel = SentryLevel.WARNING,
        tags: Map<String, String> = emptyMap(),
        extras: Map<String, Any?> = emptyMap(),
    ) {
        if (isDuplicate(dedupKey)) {
            addSampledBreadcrumb(message, level, tags, extras)
            return
        }

        Sentry.withScope { scope ->
            scope.level = level
            tags.forEach { (key, value) -> scope.setTag(key, value) }
            extras.forEach { (key, value) -> scope.setExtra(key, value?.toString() ?: "null") }
            Sentry.captureMessage(message, level)
        }
    }

    fun maskToken(token: String?): String {
        if (token.isNullOrBlank()) return "<empty>"
        if (token.length <= TOKEN_VISIBLE_CHARS) return "***"
        return "${token.take(TOKEN_SIDE_CHARS)}***${token.takeLast(TOKEN_SIDE_CHARS)}"
    }

    private fun addSampledBreadcrumb(
        message: String,
        level: SentryLevel,
        tags: Map<String, String>,
        extras: Map<String, Any?>,
    ) {
        val breadcrumb = Breadcrumb().apply {
            category = "im_event_sampled"
            this.message = message
            this.level = level
            tags.forEach { (key, value) -> setData(key, value) }
            extras.forEach { (key, value) -> if (value != null) setData(key, value) }
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    private fun isDuplicate(dedupKey: String): Boolean {
        val redis = redisTemplate
        if (redis != null) {
            return try {
                val acquired = redis.opsForValue()
                    .setIfAbsent("im_event:$dedupKey", "1", dedupWindowDuration)
                    ?: false
                !acquired
            } catch (_: Exception) {
                jvmLocalDedup(dedupKey)
            }
        }
        return jvmLocalDedup(dedupKey)
    }

    private fun jvmLocalDedup(dedupKey: String): Boolean {
        val now = System.currentTimeMillis()
        cleanupIfNeeded(now)

        val previous = lastSeen[dedupKey]
        if (previous != null && now - previous < DEDUP_WINDOW_MS) return true

        lastSeen[dedupKey] = now
        return false
    }

    private fun cleanupIfNeeded(now: Long) {
        if (lastSeen.size < MAX_LOCAL_DEDUP_KEYS) return

        val iterator = lastSeen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > DEDUP_WINDOW_MS) iterator.remove()
        }
    }

    private const val MAX_LOCAL_DEDUP_KEYS = 1024
    private const val TOKEN_SIDE_CHARS = 4
    private const val TOKEN_VISIBLE_CHARS = TOKEN_SIDE_CHARS * 2
}

@Component
@ConditionalOnBean(StringRedisTemplate::class)
class SentryReporterInitializer(
    redisTemplate: StringRedisTemplate,
) {
    init {
        SentryReporter.redisTemplate = redisTemplate
    }
}
