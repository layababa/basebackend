package com.layababateam.xinxiwang_backend.service

import java.math.BigInteger
import java.net.InetAddress

/**
 * 网络地址纯规则。
 *
 * 统一 IP host 清洗、私网/本地判断、CIDR 匹配和常用子网归一化。
 */
object NetworkAddressRules {
    private val IPV4_PATTERN = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun cleanHost(value: String): String =
        value.substringBefore("%").trim().removePrefix("[").removeSuffix("]")

    fun isPrivateOrLocal(value: String): Boolean {
        return try {
            val address = InetAddress.getByName(cleanHost(value))
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                isCarrierGradeNat(address)
        } catch (_: Exception) {
            false
        }
    }

    fun addressMatches(address: InetAddress, trusted: String): Boolean {
        if (trusted.contains("/")) return false
        return try {
            InetAddress.getByName(cleanHost(trusted)).hostAddress == address.hostAddress
        } catch (_: Exception) {
            false
        }
    }

    fun cidrContains(address: InetAddress, cidr: String): Boolean {
        if (!cidr.contains("/")) return false
        return try {
            val network = cidr.substringBefore("/")
            val prefix = cidr.substringAfter("/").toInt()
            val networkAddress = InetAddress.getByName(cleanHost(network))
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

    fun subnetOf(ip: String?): String? {
        val clean = StringValueRules.nonBlank(ip)?.let(::cleanHost) ?: return null
        val ipv4 = IPV4_PATTERN.matchEntire(clean)
        if (ipv4 != null) {
            val parts = ipv4.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
            if (parts.size == 4 && parts.all { it in 0..255 }) {
                return "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
            }
        }
        return try {
            val bytes = InetAddress.getByName(clean).address
            if (bytes.size != 16) return null
            (0 until 8 step 2).joinToString(":") { i ->
                (((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)).toString(16)
            } + "::/64"
        } catch (_: Exception) {
            null
        }
    }

    private fun isCarrierGradeNat(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 4 &&
            (bytes[0].toInt() and 0xff) == 100 &&
            (bytes[1].toInt() and 0xc0) == 64
    }
}
