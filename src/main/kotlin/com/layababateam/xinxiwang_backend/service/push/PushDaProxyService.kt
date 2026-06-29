package com.layababateam.xinxiwang_backend.service.push

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.config.PushDaConfig
import com.layababateam.xinxiwang_backend.repository.PushDaBindingRepository
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Service
class PushDaProxyService(
    private val pushDaConfig: PushDaConfig,
    private val pushDaBindingRepository: PushDaBindingRepository,
    private val pushDaRestTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun pushIfBound(userId: String, wsMessage: String) {
        if (!pushDaConfig.enabled) return

        // 缓存 binding 查询（60s）：大部分用户没绑定，缓存 "0" 避免每条消息都查 DB
        val cacheKey = "rentmsg:pushda:bound:$userId"
        val cached = try { redisTemplate.opsForValue().get(cacheKey) } catch (_: Exception) { null }
        if (cached == "0") return // 已知无绑定

        val bindings = pushDaBindingRepository.findByUserId(userId)
        try {
            redisTemplate.opsForValue().set(cacheKey, bindings.size.toString(), java.time.Duration.ofSeconds(60))
        } catch (_: Exception) {}
        if (bindings.isEmpty()) return

        val parsed = parseNotificationForPush(wsMessage) ?: return
        val (title, body, deepLink) = parsed

        try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-App-Name", pushDaConfig.appName)
                set("X-App-Secret", pushDaConfig.appSecret)
            }
            val payload = mutableMapOf<String, Any>(
                "imUid" to userId,
                "imPackage" to getImPackage(),
                "title" to title,
                "body" to body,
            )
            if (deepLink != null) payload["deepLink"] = deepLink

            pushDaRestTemplate.postForEntity(
                "${pushDaConfig.baseUrl}/api/v1/proxy/push",
                HttpEntity(payload, headers),
                Map::class.java,
            )
        } catch (e: Exception) {
            if (isNoBinding404(e)) {
                log.warn("[PushDa] skip push: userId={} has no valid proxy binding on upstream", userId)
                // 上游已判定无绑定，本地缓存 60s 避免短期重复触发 404
                try {
                    redisTemplate.opsForValue().set(cacheKey, "0", java.time.Duration.ofSeconds(60))
                } catch (_: Exception) {}
            } else {
                log.error("[PushDa] push error: userId={}, err={}", userId, e.message)
            }
        }
    }

    /**
     * 推送聚合后的群消息通知 (PushDa)。
     */
    fun pushAggregatedGroup(userId: String, title: String, body: String, convId: String) {
        if (!pushDaConfig.enabled) return

        val bindings = pushDaBindingRepository.findByUserId(userId)
        if (bindings.isEmpty()) return

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-App-Name", pushDaConfig.appName)
            set("X-App-Secret", pushDaConfig.appSecret)
        }
        val scheme = pushDaConfig.appName
        val payload = mutableMapOf<String, Any>(
            "imUid" to userId,
            "imPackage" to getImPackage(),
            "title" to title,
            "body" to body,
            "deepLink" to "$scheme://chat/$convId"
        )

        try {
            val resp = pushDaRestTemplate.postForEntity(
                "${pushDaConfig.baseUrl}/api/v1/proxy/push",
                HttpEntity(payload, headers),
                Map::class.java,
            )
        } catch (e: Exception) {
            if (isNoBinding404(e)) {
                log.warn("[PushDa聚合] skip push: userId={} has no valid proxy binding on upstream", userId)
            } else {
                log.error("[PushDa聚合] push error: userId={}, err={}", userId, e.message)
            }
        }
    }

    private fun isNoBinding404(e: Exception): Boolean {
        if (e !is HttpClientErrorException) return false
        if (e.statusCode.value() != 404) return false
        return e.responseBodyAsString.contains("无有效代理绑定")
    }

    fun reportActive(bindingUid: String) {
        if (!pushDaConfig.enabled) return
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-App-Name", pushDaConfig.appName)
            set("X-App-Secret", pushDaConfig.appSecret)
        }
        try {
            pushDaRestTemplate.postForEntity(
                "${pushDaConfig.baseUrl}/api/v1/proxy/active",
                HttpEntity(mapOf("bindingUid" to bindingUid), headers),
                Map::class.java,
            )
        } catch (e: Exception) {
            log.error("[PushDa] active report failed: bindingUid={}, err={}", bindingUid, e.message)
        }
    }

    fun unbind(bindingUid: String) {
        if (!pushDaConfig.enabled) return
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-App-Name", pushDaConfig.appName)
            set("X-App-Secret", pushDaConfig.appSecret)
        }
        try {
            pushDaRestTemplate.postForEntity(
                "${pushDaConfig.baseUrl}/api/v1/proxy/unbind",
                HttpEntity(mapOf("bindingUid" to bindingUid), headers),
                Map::class.java,
            )
        } catch (e: Exception) {
            log.error("[PushDa] unbind failed: bindingUid={}, err={}", bindingUid, e.message)
        }
    }

    fun unbindAllForUser(userId: String) {
        if (!pushDaConfig.enabled) return
        val bindings = pushDaBindingRepository.findByUserId(userId)
        for (binding in bindings) {
            unbind(binding.bindingUid)
        }
        if (bindings.isNotEmpty()) {
            pushDaBindingRepository.deleteByUserId(userId)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getStoreLinks(): Map<String, String> {
        if (!pushDaConfig.enabled) return mapOf("fallback" to "https://api.pushda.xin/download")
        val headers = HttpHeaders().apply {
            set("X-App-Name", pushDaConfig.appName)
            set("X-App-Secret", pushDaConfig.appSecret)
        }
        return try {
            val resp = pushDaRestTemplate.exchange(
                "${pushDaConfig.baseUrl}/api/v1/store-links",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                Map::class.java,
            )
            val code = (resp.body?.get("code") as? Number)?.toInt()
            if (code == 0) {
                val data = resp.body?.get("data") as? Map<*, *>
                val links = data?.get("links") as? Map<*, *>
                links?.entries?.associate { (k, v) -> k.toString() to v.toString() }
                    ?: mapOf("fallback" to "https://api.pushda.xin/download")
            } else {
                mapOf("fallback" to "https://api.pushda.xin/download")
            }
        } catch (e: Exception) {
            log.warn("[PushDa] getStoreLinks error: {}", e.message)
            mapOf("fallback" to "https://api.pushda.xin/download")
        }
    }

    private fun getImPackage(): String {
        return when (pushDaConfig.appName) {
            "linka" -> "com.xiyouji.linkamsg"
            "xinxiwang" -> "com.xmrhapsody.xinxiwangFlutter"
            "rentmsg" -> "com.caixukun.rentmsg"
            else -> pushDaConfig.appName
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNotificationForPush(wsMessage: String): Triple<String, String, String?>? {
        return try {
            val json = objectMapper.readValue(wsMessage, Map::class.java)
            val type = json["type"] as? String
            val data = json["data"] as? Map<String, Any?>

            when (type) {
                "new_message" -> {
                    val senderName = data?.get("senderName") as? String ?: "新消息"
                    val groupName = data?.get("groupName") as? String
                    val contentType = (data?.get("contentType") as? Number)?.toInt() ?: 0
                    val content = data?.get("content") as? String ?: ""
                    val convId = data?.get("conversationId") as? String
                    val isGroup = !groupName.isNullOrBlank()

                    val bodyText = when (contentType) {
                        0 -> content.take(100)
                        1 -> "[图片]"
                        2 -> "[语音]"
                        3 -> "[视频]"
                        4 -> "[文件]"
                        5 -> "[通话]"
                        6 -> "[系统通知]"
                        7, 11 -> "[红包]"
                        8 -> "[表情]"
                        10 -> "[转账]"
                        13 -> "[个人名片]"
                        14 -> "[群聊名片]"
                        16 -> "[会议]"
                        else -> "[消息]"
                    }

                    val title = if (isGroup) groupName!! else senderName
                    val body = if (isGroup) "$senderName: $bodyText" else bodyText
                    val scheme = pushDaConfig.appName
                    val deepLink = if (convId != null) "$scheme://chat/$convId" else null

                    Triple(title, body, deepLink)
                }
                "friend_request_notification" -> {
                    val fromName = data?.get("fromDisplayName") as? String ?: "有人"
                    Triple("好友请求", "${fromName}请求添加你为好友", null)
                }
                "incoming_call" -> {
                    val callerName = data?.get("callerName") as? String ?: "有人"
                    val callType = (data?.get("callType") as? Number)?.toInt() ?: 0
                    val callTypeStr = if (callType == 1) "视频" else "语音"
                    Triple("来电", "${callerName}邀请你${callTypeStr}通话", null)
                }
                else -> null
            }
        } catch (e: Exception) {
            log.debug("[PushDa] failed to parse WS message: {}", e.message)
            null
        }
    }
}
