package com.layababateam.xinxiwang_backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Simplified endpoint resolver for xianyun.
 *
 * xianyun's [com.layababateam.xinxiwang_backend.model.ServerNode] does not
 * expose an `ossPublicEndpoint` column, so we do not support per-node endpoint
 * overrides here. The current public OSS endpoint is read once from
 * configuration. When the configured endpoint is the xianyunimint accelerate
 * bucket host, media downloads are switched to the regional OSS direct host so
 * clients can still read media if the accelerate host has problems.
 */
@Service
class MediaEndpointResolver(
    @Value("\${aliyun.oss.endpoint-public}") private val fallbackEndpoint: String,
    @Value("\${aliyun.oss.endpoint-public-direct:}") private val directEndpoint: String = "",
) {
    private val cached = resolvePublicEndpoint(fallbackEndpoint, directEndpoint)

    fun currentOssPublicEndpoint(): String = cached

    fun refresh() {
        // no-op: xianyun resolves the endpoint statically from configuration
    }

    companion object {
        fun resolvePublicEndpoint(fallbackEndpoint: String, directEndpoint: String): String {
            val fallback = fallbackEndpoint.trimEnd('/')
            val direct = directEndpoint.trimEnd('/')
            return if (fallback.contains("xianyunimint", ignoreCase = true) && direct.isNotBlank()) {
                direct
            } else {
                fallback
            }
        }
    }
}
