package com.layababateam.xinxiwang_backend.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class WeakPasswordPolicyService(
    private val resourceLoader: ResourceLoader,
    @Value("\${xinxiwang.security.weak-password-denylist:classpath:security/weak_password_denylist_cn.md}")
    private val denylistLocation: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(WeakPasswordPolicyService::class.java)
        private val MARKDOWN_CODE_PATTERN = Regex("`([^`]+)`")
    }

    @Volatile
    private var denylist: Set<String> = emptySet()

    @PostConstruct
    fun load() {
        val resource = resourceLoader.getResource(denylistLocation)
        if (!resource.exists()) {
            throw IllegalStateException("Weak password denylist not found: $denylistLocation")
        }
        resource.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            denylist = parse(reader.readText())
        }
        if (denylist.isEmpty()) {
            throw IllegalStateException("Weak password denylist is empty: $denylistLocation")
        }
        log.info("Loaded weak password denylist: entries={}, source={}", denylist.size, denylistLocation)
    }

    fun isWeak(password: String?): Boolean {
        val normalized = normalize(password)
        return normalized.isNotEmpty() && normalized in denylist
    }

    fun entryCount(): Int = denylist.size

    internal fun parse(text: String): Set<String> =
        MARKDOWN_CODE_PATTERN.findAll(text)
            .map { normalize(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun normalize(password: String?): String =
        password.orEmpty().trim().lowercase(Locale.ROOT)
}
