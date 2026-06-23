package com.layababateam.xinxiwang_backend.service

import java.net.URI

object WebCustomerServiceRules {
    private val HOST_PATTERN = Regex("^(\\*\\.)?[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")
    private val THEME_COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}$")
    private val IMAGE_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    fun normalizeAllowedDomains(domains: List<String>): List<String> =
        domains
            .mapNotNull { normalizeAllowedDomain(it) }
            .distinct()

    fun normalizeAllowedDomain(domain: String?): String? {
        val raw = domain?.trim()?.lowercase()?.trimEnd('.') ?: return null
        if (raw.isBlank()) return null
        return raw.takeIf { HOST_PATTERN.matches(it) }
    }

    fun isValidAllowedDomains(domains: List<String>): Boolean =
        normalizeAllowedDomains(domains).isNotEmpty() &&
            normalizeAllowedDomains(domains).size == domains.map { it.trim() }.filter { it.isNotBlank() }.size

    fun isValidThemeColor(value: String): Boolean =
        THEME_COLOR_PATTERN.matches(value.trim())

    fun normalizeThemeColor(value: String?): String =
        value?.trim()?.takeIf(::isValidThemeColor)?.lowercase() ?: "#2563eb"

    fun sourceHost(origin: String?, referer: String?): String? =
        normalizedHost(origin) ?: normalizedHost(referer)

    fun isOriginAllowed(originOrUrl: String?, allowedDomains: List<String>): Boolean {
        val host = normalizedHost(originOrUrl) ?: return false
        return isHostAllowed(host, normalizeAllowedDomains(allowedDomains))
    }

    fun isSourceAllowed(origin: String?, referer: String?, allowedDomains: List<String>): Boolean {
        val host = sourceHost(origin, referer) ?: return false
        return isHostAllowed(host, normalizeAllowedDomains(allowedDomains))
    }

    fun isImageContentTypeAllowed(contentType: String?): Boolean =
        contentType?.substringBefore(';')?.trim()?.lowercase() in IMAGE_CONTENT_TYPES

    fun trimToNull(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() }

    private fun isHostAllowed(host: String, allowedDomains: List<String>): Boolean =
        allowedDomains.any { domain ->
            when {
                domain.startsWith("*.") -> {
                    val suffix = domain.removePrefix("*.")
                    host.endsWith(".$suffix") && host.length > suffix.length + 1
                }
                else -> host == domain
            }
        }

    private fun normalizedHost(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val host = runCatching {
            URI(raw).host
        }.getOrNull() ?: raw.substringBefore('/').substringBefore(':')
        return host.trim().lowercase().trimEnd('.').takeIf { it.isNotBlank() }
    }
}
