package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ServerNode
import com.layababateam.xinxiwang_backend.repository.ServerNodeRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Resolves the public OSS endpoints used when generating media URLs.
 * Node-managed endpoints win; application properties remain the bootstrap
 * fallback for empty node tables or nodes without OSS endpoint fields.
 */
@Service
class MediaEndpointResolver(
    @Value("\${aliyun.oss.endpoint-public}") private val fallbackEndpoint: String,
    @Value("\${aliyun.oss.endpoint-public-direct:}") private val directEndpoint: String = "",
    private val serverNodeRepository: ServerNodeRepository,
) {
    @Volatile
    private var cached = loadEndpoints()

    fun currentOssPublicEndpoint(): String = cached.publicEndpoint

    fun currentOssFailbackEndpoint(): String = cached.failbackEndpoint

    fun refresh() {
        cached = loadEndpoints()
    }

    private fun loadEndpoints(): ResolvedEndpoints {
        val configured = resolveConfiguredEndpoint()
        val sourceNode = runCatching {
            serverNodeRepository.findByEnabledTrueOrderBySortOrderAsc()
        }.getOrDefault(emptyList())
            .sortedWith(compareBy<ServerNode> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
            .firstOrNull()

        val public = normalizeRoot(sourceNode?.ossPublicEndpoint)
            ?: configured
        val failback = normalizeRoot(sourceNode?.ossFailbackEndpoint)
            ?: configured
        return ResolvedEndpoints(public, failback)
    }

    private fun normalizeRoot(value: String?): String? =
        value?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

    private fun resolveConfiguredEndpoint(): String =
        resolvePublicEndpoint(fallbackEndpoint, directEndpoint)

    private data class ResolvedEndpoints(
        val publicEndpoint: String,
        val failbackEndpoint: String,
    )

    companion object {
        fun resolvePublicEndpoint(fallbackEndpoint: String, directEndpoint: String): String {
            val fallback = UrlRules.stripTrailingSlash(fallbackEndpoint)
            val direct = UrlRules.stripTrailingSlash(directEndpoint)
            return if (fallback.contains("xianyunimint", ignoreCase = true) && direct.isNotBlank()) {
                direct
            } else {
                fallback
            }
        }
    }
}
