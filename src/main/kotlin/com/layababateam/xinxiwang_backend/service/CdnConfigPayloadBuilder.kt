package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ServerNode

class CdnConfigPayloadBuilder(
    private val defaultOssPublicEndpoint: String,
    private val defaultOssFailbackEndpoint: String,
) {
    fun build(
        nodes: List<ServerNode>,
        versionMillis: Long = System.currentTimeMillis(),
    ): Map<String, Any?> {
        val sortedNodes = nodes
            .asSequence()
            .filter { it.enabled }
            .sortedWith(compareBy<ServerNode> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
            .toList()
        val grouped = sortedNodes.groupBy { it.region }
        val nodeMaps = sortedNodes.map(::nodeToMap)

        val payload = linkedMapOf<String, Any?>(
            "version" to versionMillis,
            "nodes" to nodeMaps,
            "china" to (grouped["china"] ?: emptyList()).map(::nodeToMap),
            "international" to (grouped["international"] ?: emptyList()).map(::nodeToMap),
        )

        val sourceNode = sortedNodes.firstOrNull()
        val primaryRoot = firstUsableHttpRoot(sourceNode?.ossPublicEndpoint, defaultOssPublicEndpoint)
        if (primaryRoot != null) {
            val failbackRoot = firstUsableHttpRoot(sourceNode?.ossFailbackEndpoint, defaultOssFailbackEndpoint)
                ?: primaryRoot
            payload["oss_url"] = configUrl(primaryRoot)
            payload["oss_failback_url"] = configUrl(failbackRoot)
        }

        return payload
    }

    private fun nodeToMap(node: ServerNode) = linkedMapOf<String, Any?>(
        "id" to node.id,
        "name" to node.name,
        "appServerUrl" to node.appServerUrl,
        "websocketUrl" to node.websocketUrl,
        "baseUrl" to node.baseUrl,
        "speedTestUrl" to "${node.appServerUrl}/ping",
    )

    private fun firstUsableHttpRoot(vararg values: String?): String? {
        return values
            .asSequence()
            .mapNotNull(AdminNodeOssEndpointRules::normalizeRootUrlOrNull)
            .firstOrNull {
                AdminNodeOssEndpointRules.isHttpUrl(it) &&
                    !it.contains("/config/cdn.json", ignoreCase = true)
            }
    }

    private fun configUrl(root: String): String = "${root.trimEnd('/')}/config/cdn.json"
}
