package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ClientVersionRule

/**
 * Client version helpers for update policy decisions.
 *
 * Versions are ordered by semantic segments first, then by the build suffix
 * after '+'. Specific-version rules without a build suffix match all builds
 * for that semantic version.
 */
object ClientVersionRules {
    val supportedPlatforms: Set<String> = setOf("ios", "ipados", "android", "windows", "macos")

    fun resolveUpdateUrl(platform: String, customUrl: String?): String =
        ClientVersionRule.getUpdateUrl(platform, customUrl)

    fun normalizePlatform(platform: String?): String? =
        nonBlank(platform)?.lowercase()?.takeIf { it in supportedPlatforms }

    fun isSupportedPlatform(platform: String?): Boolean =
        normalizePlatform(platform) != null

    fun compareVersions(v1: String, v2: String): Int {
        val left = ParsedVersion.parse(v1)
        val right = ParsedVersion.parse(v2)
        val maxSegments = maxOf(left.core.size, right.core.size)
        for (index in 0 until maxSegments) {
            val cmp = left.core.getOrElse(index) { 0 }.compareTo(right.core.getOrElse(index) { 0 })
            if (cmp != 0) return cmp
        }
        return left.build.compareTo(right.build)
    }

    fun specificVersionMatches(current: String, target: String): Boolean {
        val left = ParsedVersion.parse(current)
        val right = ParsedVersion.parse(target)
        val maxSegments = maxOf(left.core.size, right.core.size)
        for (index in 0 until maxSegments) {
            if (left.core.getOrElse(index) { 0 } != right.core.getOrElse(index) { 0 }) return false
        }
        return if (target.trim().contains("+")) left.build == right.build else true
    }

    fun versionInRange(version: String, minVersion: String?, maxVersion: String?): Boolean {
        val min = nonBlank(minVersion)
        val max = nonBlank(maxVersion)
        if (min != null && compareVersions(version, min) < 0) return false
        if (max != null && compareVersions(version, max) > 0) return false
        return true
    }

    private fun nonBlank(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private data class ParsedVersion(
        val core: List<Int>,
        val build: Int,
    ) {
        companion object {
            fun parse(value: String): ParsedVersion {
                val parts = value.trim().split("+", limit = 2)
                val core = parts.firstOrNull()
                    ?.split(".")
                    ?.map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(0)
                val build = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
                return ParsedVersion(core, build)
            }
        }
    }
}
