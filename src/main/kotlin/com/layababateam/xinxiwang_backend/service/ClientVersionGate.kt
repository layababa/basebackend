package com.layababateam.xinxiwang_backend.service

import org.springframework.stereotype.Component

@Component
class ClientVersionGate {
    private val minVersion = listOf(1, 0, 6)
    private val minBuild = 75
    private val pattern = Regex("""^(\d+)\.(\d+)\.(\d+)\+(\d+)$""")

    fun isEligible(clientVersion: String?, supportsClientLogConfig: Boolean): Boolean {
        if (!supportsClientLogConfig || clientVersion.isNullOrBlank()) return false
        val match = pattern.matchEntire(clientVersion.trim()) ?: return false
        val version = listOf(
            match.groupValues[1].toIntOrNull() ?: return false,
            match.groupValues[2].toIntOrNull() ?: return false,
            match.groupValues[3].toIntOrNull() ?: return false,
        )
        val build = match.groupValues[4].toIntOrNull() ?: return false
        return compareVersion(version, minVersion) > 0 && build > minBuild
    }

    private fun compareVersion(left: List<Int>, right: List<Int>): Int {
        for (i in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(i) { 0 }
            val b = right.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
