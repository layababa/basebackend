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
    @Value("\${app.environment:\${sentry.environment:production}}") private val appEnvironment: String,
) : MediaKeySnapshotProvider {
    private val log = LoggerFactory.getLogger(MediaKeyRegistry::class.java)
    private val keys = ConcurrentHashMap<String, ByteArray>()

    @PostConstruct
    fun init() {
        StringListRules.delimited(keysConfig)
            .forEach { entry -> parseEntry(entry) }

        if (!keys.containsKey(currentId)) {
            keys[currentId] = guardOrFallback(
                "current-id='$currentId' 不在 xinxiwang.media.master-keys 中",
            )
        }
    }

    /**
     * 缺密钥/占位符时的环境分流：
     * - local/dev/test（非 staging/production）→ 兜底生成临时随机密钥 + warn（本地体验不变）。
     * - staging/production → fail-fast 抛 IllegalStateException 拒绝启动，
     *   避免临时密钥每次重启变化导致历史媒体密文永久不可解密。
     * SDK 不保存密钥值；判定开关读自接入方配置 app.environment，不内置环境。
     */
    private fun guardOrFallback(reason: String): ByteArray {
        val env = appEnvironment.trim().lowercase()
        val isProd = env == "production" || env == "staging"
        check(!isProd) {
            "媒体主密钥未配置/为占位符（$reason），$env 环境拒绝使用临时随机密钥" +
                "（重启后历史密文不可解密）；请设置 XINXIWANG_MEDIA_KEYS"
        }
        log.warn(
            "媒体主密钥未配置/为占位符（{}），{} 环境生成 EPHEMERAL 临时密钥（仅本地/开发）",
            reason, env,
        )
        return randomKey()
    }

    private fun parseEntry(entry: String) {
        val idx = entry.indexOf(':')
        require(idx > 0) { "Invalid xinxiwang.media.master-keys entry: $entry" }
        val keyId = entry.substring(0, idx).trim()
        val b64 = entry.substring(idx + 1).trim()

        if (b64 == PLACEHOLDER_KEY_VALUE) {
            keys[keyId] = guardOrFallback("keyId='$keyId' 为占位符值")
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
