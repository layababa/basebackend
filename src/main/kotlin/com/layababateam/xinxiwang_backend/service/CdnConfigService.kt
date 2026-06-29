package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.repository.ServerNodeRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

import com.layababateam.xinxiwang_backend.model.ServerNode
/**
 * Publishes `config/cdn.json` so the Flutter client bootstrap can discover
 * available server nodes.  Historically the file was mirrored to S3 only; the
 * OSS migration adds a parallel publish target so we can cut over without
 * breaking existing clients.
 *
 * Target selection is driven by `cdn.publish-target`:
 *
 *  - `s3`   — legacy behaviour, publish to S3 only (requires [s3Client])
 *  - `oss`  — publish to Aliyun OSS only (default)
 *  - `both` — publish to both simultaneously
 *
 * When the legacy S3 stack is fully decommissioned the [s3Client] dependency
 * becomes optional, so we accept `required = false` and log a warning if we
 * are asked to publish to S3 without it.
 */
@Service
class CdnConfigService(
    @Autowired(required = false) private val s3Client: S3Client?,
    private val ossService: OssService,
    private val serverNodeRepository: ServerNodeRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${media.bucket_general_name}") private val bucketName: String,
    @Value("\${cdn.publish-target:oss}") private val publishTarget: String,
    @Value("\${aliyun.oss.endpoint-public}") private val ossEndpointPublic: String,
    @Value("\${aliyun.oss.endpoint-public-direct:}") private val ossEndpointPublicDirect: String = "",
) {
    private val log = LoggerFactory.getLogger(CdnConfigService::class.java)

    companion object {
        private const val CDN_CONFIG_KEY = "config/cdn.json"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        try {
            publishCdnConfig()
        } catch (e: Exception) {
            log.warn("启动时发布 cdn.json 失败，旧配置仍可用", e)
        }
    }

    @Synchronized
    fun publishCdnConfig() {
        val nodes = serverNodeRepository.findByEnabledTrueOrderBySortOrderAsc()

        if (nodes.isEmpty()) {
            log.warn("[CdnConfig] 节点表为空，跳过发布以保护已发布的 cdn.json")
            return
        }
        val cdnConfig = buildCdnConfig(nodes)
        val jsonBytes = objectMapper.writeValueAsBytes(cdnConfig)

        val target = publishTarget.lowercase().trim()
        val publishToS3 = target == "s3" || target == "both"
        val publishToOss = target == "oss" || target == "both"

        if (publishToS3) {
            publishToS3(jsonBytes)
        }
        if (publishToOss) {
            publishToOss(jsonBytes)
        }
        if (!publishToS3 && !publishToOss) {
            log.warn("[CdnConfig] 未知的 cdn.publish-target='{}'，已跳过发布", publishTarget)
        }
    }

    fun currentCdnConfig(): Map<String, Any?> =
        buildCdnConfig(serverNodeRepository.findByEnabledTrueOrderBySortOrderAsc())

    private fun buildCdnConfig(nodes: List<ServerNode>): Map<String, Any?> {
        val sortedNodes = nodes.sortedWith(compareBy<ServerNode> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id ?: "" })
        val grouped = sortedNodes.groupBy { it.region }
        val allNodeMaps = sortedNodes.map { nodeToMap(it) }
        val urls = cdnConfigUrls(sortedNodes)

        return mapOf(
            "version" to System.currentTimeMillis(),
            "oss_url" to urls.first,
            "oss_failback_url" to urls.second,
            "nodes" to allNodeMaps,
            "china" to (grouped["china"] ?: emptyList()).map { nodeToMap(it) },
            "international" to (grouped["international"] ?: emptyList()).map { nodeToMap(it) },
            "staging" to (grouped["staging"] ?: emptyList()).map { nodeToMap(it) }
        )
    }

    private fun cdnConfigUrls(nodes: List<ServerNode>): Pair<String, String> {
        val firstNode = nodes.firstOrNull()
        val publicEndpoint = normalizeEndpoint(firstNode?.ossPublicEndpoint)
            ?: normalizeEndpoint(ossEndpointPublic)
            ?: ""
        val failbackEndpoint = normalizeEndpoint(firstNode?.ossFailbackEndpoint)
            ?: normalizeEndpoint(ossEndpointPublicDirect)
            ?: publicEndpoint
        return cdnJsonUrl(publicEndpoint) to cdnJsonUrl(failbackEndpoint)
    }

    private fun cdnJsonUrl(endpoint: String): String =
        "${endpoint.trimEnd('/')}/config/cdn.json"

    private fun normalizeEndpoint(value: String?): String? =
        value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

    private fun nodeToMap(node: ServerNode): Map<String, Any?> {
        val appServerUrl = node.appServerUrl.trimEnd('/')
        val websocketUrl = node.websocketUrl.trimEnd('/')
        val baseUrl = node.baseUrl.trimEnd('/')
        return mapOf(
            "id" to node.id,
            "name" to node.name,
            "appServerUrl" to appServerUrl,
            "websocketUrl" to websocketUrl,
            "baseUrl" to baseUrl,
            "speedTestUrl" to "${appServerUrl}/ping"
        )
    }

    private fun publishToS3(jsonBytes: ByteArray) {
        val client = s3Client
        if (client == null) {
            log.warn("[CdnConfig] publishTarget 需要 S3 但 S3Client 未注入，跳过 S3 发布")
            return
        }
        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(CDN_CONFIG_KEY)
            .contentType("application/json")
            .cacheControl("max-age=300")
            .build()

        client.putObject(putRequest, RequestBody.fromBytes(jsonBytes))
        log.info("cdn.json 已发布到 S3，size={}B", jsonBytes.size)
    }

    private fun publishToOss(jsonBytes: ByteArray) {
        ossService.putPublicObject(CDN_CONFIG_KEY, jsonBytes, "application/json")
        log.info("cdn.json 已发布到 OSS，size={}B", jsonBytes.size)
    }
}
