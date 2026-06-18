package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.service.EncodingRules
import com.layababateam.xinxiwang_backend.service.StringListRules
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Component
class MediaKeyRegistry(
    @Value("\${xinxiwang.media.master-keys}") private val keysConfig: String,
    @Value("\${xinxiwang.media.master-key-current-id}") private val currentId: String,
) : MediaKeySnapshotProvider {
    private val log = LoggerFactory.getLogger(MediaKeyRegistry::class.java)
    private val keys = ConcurrentHashMap<String, ByteArray>()

    @PostConstruct
    fun init() {
        StringListRules.delimited(keysConfig)
            .forEach { entry -> parseEntry(entry) }

        if (!keys.containsKey(currentId)) {
            log.warn(
                "xinxiwang.media.master-key-current-id='{}' not present in xinxiwang.media.master-keys; " +
                    "generating an EPHEMERAL in-memory key for local development. " +
                    "Set XINXIWANG_MEDIA_KEYS in production!",
                currentId,
            )
            keys[currentId] = randomKey()
        }
    }

    private fun parseEntry(entry: String) {
        val idx = entry.indexOf(':')
        require(idx > 0) { "Invalid xinxiwang.media.master-keys entry: $entry" }
        val keyId = entry.substring(0, idx).trim()
        val b64 = entry.substring(idx + 1).trim()

        if (b64 == PLACEHOLDER_KEY_VALUE) {
            log.warn(
                "xinxiwang.media.master-keys contains placeholder value for keyId '{}'. " +
                    "Generating an EPHEMERAL in-memory key for local development only. " +
                    "Set XINXIWANG_MEDIA_KEYS to real base64-encoded 32-byte keys before deploying!",
                keyId,
            )
            keys[keyId] = randomKey()
            return
        }

        val raw = try {
            EncodingRules.decodeBase64(b64)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                "Master key '$keyId' is not valid base64 (and not the dev placeholder). " +
                    "Set XINXIWANG_MEDIA_KEYS to comma-separated 'id:base64' entries.",
                e,
            )
        }
        require(raw.size == KEY_LENGTH_BYTES) {
            "Master key '$keyId' must be $KEY_LENGTH_BYTES bytes (got ${raw.size})"
        }
        keys[keyId] = raw
    }

    private fun randomKey(): ByteArray = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

    fun currentKeyId(): String = currentId

    fun currentKey(): ByteArray = keys[currentId]!!.copyOf()

    fun keyById(keyId: String): ByteArray? = keys[keyId]?.copyOf()

    override fun snapshotForClient(): Map<String, Any> {
        val keysList = keys.entries
            .sortedBy { it.key }
            .map { (id, raw) ->
                mapOf(
                    "keyId" to id,
                    "keyB64" to EncodingRules.base64(raw),
                )
            }
        return mapOf(
            "currentKeyId" to currentId,
            "alg" to "AES-256-GCM",
            "keys" to keysList,
        )
    }

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val PLACEHOLDER_KEY_VALUE = "CHANGE_ME_BASE64_32_BYTES_KEY"
    }
}
