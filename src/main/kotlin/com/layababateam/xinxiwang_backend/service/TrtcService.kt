package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Arrays
import java.util.Base64
import java.util.zip.Deflater
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class TrtcService(
    private val objectMapper: ObjectMapper,
    @Value("\${tencent.asr.secret-id}") private val secretId: String,
    @Value("\${tencent.asr.secret-key}") private val cloudSecretKey: String,
) : TrtcRoomUsersPort {
    private val log = LoggerFactory.getLogger(TrtcService::class.java)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    companion object {
        const val SDK_APP_ID = 20030819L
        private const val SECRET_KEY = "e1a26911722b2f962bb1842e4b553b7cd218529cf036ffafe5c2f815a6cfa858"
        private const val DEFAULT_EXPIRE = 86400L
        private const val TRTC_API_HOST = "trtc.tencentcloudapi.com"
    }

    fun genUserSig(userId: String, expire: Long = DEFAULT_EXPIRE): String {
        log.info("[TRTC-SIG] Generating UserSig for userId={}, sdkAppId={}, expire={}s", userId, SDK_APP_ID, expire)
        val currTime = System.currentTimeMillis() / 1000
        val sig = hmacSha256(userId, currTime, expire)

        val sigDoc = LinkedHashMap<String, Any>()
        sigDoc["TLS.ver"] = "2.0"
        sigDoc["TLS.identifier"] = userId
        sigDoc["TLS.sdkappid"] = SDK_APP_ID
        sigDoc["TLS.expire"] = expire
        sigDoc["TLS.time"] = currTime
        sigDoc["TLS.sig"] = sig

        val jsonBytes = objectMapper.writeValueAsBytes(sigDoc)
        log.info("[TRTC-SIG] SigDoc JSON size={} bytes, time={}, expireAt={}", jsonBytes.size, currTime, currTime + expire)

        val compressor = Deflater()
        compressor.setInput(jsonBytes)
        compressor.finish()
        val buf = ByteArray(4096)
        val len = compressor.deflate(buf)
        compressor.end()

        // Tencent TLS Sig API v2: use standard Base64 then replace +→*, /→-, =→_
        val result = Base64.getEncoder().encodeToString(Arrays.copyOfRange(buf, 0, len))
            .replace("+", "*")
            .replace("/", "-")
            .replace("=", "_")
        log.info("[TRTC-SIG] UserSig generated for userId={}, length={} chars, first10={}", userId, result.length, result.take(10))
        return result
    }

    private fun hmacSha256(identifier: String, currTime: Long, expire: Long): String {
        val content = "TLS.identifier:$identifier\n" +
                "TLS.sdkappid:$SDK_APP_ID\n" +
                "TLS.time:$currTime\n" +
                "TLS.expire:$expire\n"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET_KEY.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    // ─── TRTC REST API: 查询房间内用户 ──────────────────────────────────

    fun isUserInRoom(roomId: Int, userId: String): Boolean {
        return try {
            val users = describeRoomUsers(roomId)
            val inRoom = users.contains(userId)
            log.info("[TRTC-API] isUserInRoom roomId={} userId={} result={} (usersInRoom={})", roomId, userId, inRoom, users)
            inRoom
        } catch (e: Exception) {
            log.warn("[TRTC-API] Failed to query room {} for user {}: {}", roomId, userId, e.message)
            // 查询失败时保守处理：假定用户还在房间，不贸然结束通话
            true
        }
    }

    override fun activeRoomUsers(roomId: Int): List<String>? {
        return try {
            val users = describeRoomUsers(roomId)
            log.info("[TRTC-API] activeRoomUsers roomId={} usersInRoom={}", roomId, users)
            users
        } catch (e: Exception) {
            log.warn("[TRTC-API] Failed to query room {} users: {}", roomId, e.message)
            null
        }
    }

    private fun describeRoomUsers(roomId: Int): List<String> {
        val action = "DescribeUserInformation"
        val version = "2019-07-22"
        val timestamp = Instant.now().epochSecond
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(timestamp))

        val payload = objectMapper.writeValueAsString(mapOf(
            "CommId" to mapOf(
                "SdkAppId" to SDK_APP_ID,
                "RoomNumber" to roomId,
                "StartTime" to (timestamp - 86400),
                "EndTime" to timestamp,
            ),
            "MaxNum" to 10,
        ))

        val authorization = signTC3(action, version, timestamp, date, payload)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://$TRTC_API_HOST"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json; charset=utf-8")
            // 不手动设置 Host：JDK HttpClient 禁止受限头(restricted header name: "Host")，
            // 且会自动按 URI 填入 Host=TRTC_API_HOST，与 signTC3 签名里的 host 一致。
            .header("Authorization", authorization)
            .header("X-TC-Action", action)
            .header("X-TC-Version", version)
            .header("X-TC-Timestamp", timestamp.toString())
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = objectMapper.readTree(response.body())
        val responseNode = body.path("Response")

        if (responseNode.has("Error")) {
            val errCode = responseNode.path("Error").path("Code").asText()
            val errMsg = responseNode.path("Error").path("Message").asText()
            log.warn("[TRTC-API] DescribeUserInformation error: {} - {}", errCode, errMsg)
            return emptyList()
        }

        val userList = responseNode.path("UserList")
        if (!userList.isArray) return emptyList()

        return userList.mapNotNull { node ->
            val uid = node.path("UserId").asText("")
            val finished = node.path("Finished").asBoolean(true)
            if (uid.isNotEmpty() && !finished) uid else null
        }
    }

    private fun signTC3(action: String, version: String, timestamp: Long, date: String, payload: String): String {
        val service = "trtc"
        val canonicalRequest = "POST\n/\n\ncontent-type:application/json; charset=utf-8\nhost:$TRTC_API_HOST\n\ncontent-type;host\n${sha256Hex(payload)}"
        val credentialScope = "$date/$service/tc3_request"
        val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val secretDate = hmacSha256Bytes("TC3${cloudSecretKey}", date)
        val secretService = hmacSha256Bytes(secretDate, service)
        val secretSigning = hmacSha256Bytes(secretService, "tc3_request")
        val signature = hmacSha256Bytes(secretSigning, stringToSign).joinToString("") { "%02x".format(it) }

        return "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=content-type;host, Signature=$signature"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Bytes(key: String, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacSha256Bytes(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
}
