package com.layababateam.xinxiwang_backend.service

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CustomerServiceQrCandidate(
    val bindingId: String,
    val customerServiceId: String,
    val assignedCount: Long,
    val sortOrder: Int,
    val createdAt: Long,
    val bindingEnabled: Boolean = true,
    val accountEnabled: Boolean = true,
)

object CustomerServiceQrAssignmentRules {
    fun selectBinding(candidates: List<CustomerServiceQrCandidate>): CustomerServiceQrCandidate? =
        candidates
            .filter { it.bindingEnabled && it.accountEnabled }
            .minWithOrNull(
                compareBy<CustomerServiceQrCandidate> { it.assignedCount }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt },
            )

    fun buildQrUrl(publicBaseUrl: String, code: String): String {
        val base = publicBaseUrl.trim().trimEnd('/').removeSuffix("/api")
        val encoded = URLEncoder.encode(code, StandardCharsets.UTF_8)
        return "$base/customerservice?=$encoded"
    }

    fun buildLandingUrl(adminRouteBaseUrl: String, code: String): String {
        val base = adminRouteBaseUrl.trim().trimEnd('/')
        val normalizedBase = if (base.endsWith("/admin")) base else "$base/admin"
        val encoded = URLEncoder.encode(code, StandardCharsets.UTF_8)
        return "$normalizedBase/customer-service/qr/$encoded"
    }
}
