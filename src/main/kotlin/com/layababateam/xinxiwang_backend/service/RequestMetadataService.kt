package com.layababateam.xinxiwang_backend.service

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
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
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

    fun from(request: HttpServletRequest): RequestMetadata {
        val remoteAddr = request.remoteAddr?.takeIf { it.isNotBlank() }
        val forwardedFor = request.getHeader("X-Forwarded-For")?.takeIf { it.isNotBlank() }
        val realIp = request.getHeader("X-Real-IP")?.takeIf { it.isNotBlank() }
        val forwarded = request.getHeader("Forwarded")?.takeIf { it.isNotBlank() }
        val userAgent = request.getHeader("User-Agent")?.takeIf { it.isNotBlank() }
        val deviceId = request.getHeader("X-Admin-Device-Id")?.takeIf { it.isNotBlank() }?.take(128)
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
        val host = value.substringBefore("%").trim().removePrefix("[").removeSuffix("]")
        return try {
            val address = InetAddress.getByName(host)
            configuredTrustedProxies.any { trusted ->
                trusted == host || trustedAddressMatches(address, trusted) || cidrContains(address, trusted)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateOrLocalAddress(value: String): Boolean {
        val host = value.substringBefore("%").trim().removePrefix("[").removeSuffix("]")
        return try {
            val address = InetAddress.getByName(host)
            address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress ||
                    isCarrierGradeNat(address)
        } catch (_: Exception) {
            false
        }
    }

    private fun isCarrierGradeNat(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 4 &&
                (bytes[0].toInt() and 0xff) == 100 &&
                (bytes[1].toInt() and 0xc0) == 64
    }

    private fun trustedAddressMatches(address: InetAddress, trusted: String): Boolean {
        if (trusted.contains("/")) return false
        return try {
            InetAddress.getByName(trusted).hostAddress == address.hostAddress
        } catch (_: Exception) {
            false
        }
    }

    private fun cidrContains(address: InetAddress, cidr: String): Boolean {
        if (!cidr.contains("/")) return false
        return try {
            val network = cidr.substringBefore("/")
            val prefix = cidr.substringAfter("/").toInt()
            val networkAddress = InetAddress.getByName(network)
            val addressBytes = address.address
            val networkBytes = networkAddress.address
            if (addressBytes.size != networkBytes.size) return false
            val bitCount = addressBytes.size * 8
            if (prefix !in 0..bitCount) return false
            val shift = bitCount - prefix
            BigInteger(1, addressBytes).shiftRight(shift) == BigInteger(1, networkBytes).shiftRight(shift)
        } catch (_: Exception) {
            false
        }
    }
}
