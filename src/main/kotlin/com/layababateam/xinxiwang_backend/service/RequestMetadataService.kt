package com.layababateam.xinxiwang_backend.service

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.InetAddress

data class RequestMetadata(
    val clientIp: String?,
    val remoteAddr: String?,
    val forwardedFor: String?,
    val realIp: String?,
    val forwarded: String?,
    val userAgent: String?,
    val deviceId: String?,
    val deviceSummary: String?,
    val proxyChain: String?,
    val trustedProxyConfigured: Boolean = false,
    val remoteAddrPrivate: Boolean = false,
    val untrustedProxyHeaders: Boolean = false
)

@Service
class RequestMetadataService(
    @Value("\${xinxiwang.admin.audit.trusted-proxies:}") trustedProxyConfig: String
) {
    private val configuredTrustedProxies = trustedProxyConfig
        .let(StringListRules::delimited)
        .toSet()

    fun from(request: HttpServletRequest): RequestMetadata {
        val remoteAddr = StringValueRules.nonBlank(request.remoteAddr)
        val forwardedFor = StringValueRules.nonBlank(request.getHeader("X-Forwarded-For"))
        val realIp = StringValueRules.nonBlank(request.getHeader("X-Real-IP"))
        val forwarded = StringValueRules.nonBlank(request.getHeader("Forwarded"))
        val userAgent = StringValueRules.nonBlank(request.getHeader("User-Agent"))
        val deviceId = StringValueRules.nonBlank(request.getHeader("X-Admin-Device-Id"), max = 128)
        val hasProxyHeaders = !forwardedFor.isNullOrBlank() || !realIp.isNullOrBlank() || !forwarded.isNullOrBlank()

        val forwardedIps = RequestMetadataRules.forwardedIps(forwardedFor)
        val remoteTrusted = remoteAddr?.let(::isTrustedProxyAddress) == true
        val remotePrivate = remoteAddr?.let(::isPrivateOrLocalAddress) == true
        val clientIp = when {
            remoteTrusted && forwardedIps.isNotEmpty() -> forwardedIps.asReversed().firstOrNull { !isTrustedProxyAddress(it) } ?: forwardedIps.first()
            remoteTrusted && !realIp.isNullOrBlank() -> realIp
            else -> remoteAddr
        }

        return RequestMetadata(
            clientIp = clientIp,
            remoteAddr = remoteAddr,
            forwardedFor = forwardedFor,
            realIp = realIp,
            forwarded = forwarded,
            userAgent = userAgent,
            deviceId = deviceId,
            deviceSummary = summarizeUserAgent(userAgent),
            proxyChain = forwardedFor,
            trustedProxyConfigured = configuredTrustedProxies.isNotEmpty(),
            remoteAddrPrivate = remotePrivate,
            untrustedProxyHeaders = hasProxyHeaders && !remoteTrusted
        )
    }

    private fun summarizeUserAgent(userAgent: String?): String? {
        if (userAgent.isNullOrBlank()) return null
        val browser = when {
            userAgent.contains("Edg/", ignoreCase = true) -> "Edge"
            userAgent.contains("Chrome/", ignoreCase = true) -> "Chrome"
            userAgent.contains("Firefox/", ignoreCase = true) -> "Firefox"
            userAgent.contains("Safari/", ignoreCase = true) -> "Safari"
            else -> "Unknown Browser"
        }
        val os = when {
            userAgent.contains("Windows", ignoreCase = true) -> "Windows"
            userAgent.contains("Mac OS X", ignoreCase = true) -> "macOS"
            userAgent.contains("Android", ignoreCase = true) -> "Android"
            userAgent.contains("iPhone", ignoreCase = true) || userAgent.contains("iPad", ignoreCase = true) -> "iOS"
            userAgent.contains("Linux", ignoreCase = true) -> "Linux"
            else -> "Unknown OS"
        }
        return "$browser / $os"
    }

    private fun isTrustedProxyAddress(value: String): Boolean {
        if (configuredTrustedProxies.isEmpty()) return false
        val host = NetworkAddressRules.cleanHost(value)
        return try {
            val address = InetAddress.getByName(host)
            configuredTrustedProxies.any { trusted ->
                trusted == host ||
                    NetworkAddressRules.addressMatches(address, trusted) ||
                    NetworkAddressRules.cidrContains(address, trusted)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateOrLocalAddress(value: String): Boolean =
        NetworkAddressRules.isPrivateOrLocal(value)
}
