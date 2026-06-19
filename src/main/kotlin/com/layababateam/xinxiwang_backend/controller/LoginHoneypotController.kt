package com.layababateam.xinxiwang_backend.controller

import tools.jackson.databind.json.JsonMapper
import com.layababateam.xinxiwang_backend.dto.AuthResponse
import com.layababateam.xinxiwang_backend.service.LoginHoneypotPort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class LoginHoneypotController(
    private val loginHoneypotPort: LoginHoneypotPort,
    private val objectMapper: JsonMapper,
) {
    @RequestMapping(
        "/api/auth/login-admin",
        "/api/auth/login.php",
        "/api/admin/auth/login.php",
        "/api/admin/login",
        "/admin/login",
        "/wp-login.php",
    )
    fun honeypot(request: HttpServletRequest): ResponseEntity<AuthResponse> {
        val body = requestValues(request)
        val username = firstValue(body, "username", "user", "account", "login")
            ?.take(USERNAME_MAX_LENGTH)
            ?: DEFAULT_HONEYPOT_USERNAME
        loginHoneypotPort.recordHoneypot(
            username = username,
            request = request,
            deviceId = firstValue(body, "deviceId", "device_id"),
            deviceName = firstValue(body, "deviceName", "device_name"),
            platform = firstValue(body, "platform"),
            clientVersion = firstValue(body, "clientVersion", "client_version"),
        )
        return ResponseEntity.badRequest().body(AuthResponse(success = false, message = LOGIN_FAILED_MESSAGE))
    }

    private fun requestValues(request: HttpServletRequest): Map<String, String> {
        val values = linkedMapOf<String, String>()
        request.parameterMap.forEach { (key, rawValues) ->
            rawValues.firstOrNull()?.takeIf { it.isNotBlank() }?.let { values[key] = it }
        }

        val contentType = request.contentType.orEmpty()
        if (values.isEmpty() && contentType.contains(JSON_CONTENT_TYPE, ignoreCase = true)) {
            readJsonBody(request).forEach { (key, value) ->
                values[key] = value
            }
        }
        return values
    }

    private fun readJsonBody(request: HttpServletRequest): Map<String, String> {
        return runCatching {
            val body = request.reader.readText().take(MAX_BODY_CHARS)
            if (body.isBlank()) return emptyMap()
            objectMapper.readValue(body, Map::class.java)
                .mapNotNull { (key, value) ->
                    val normalizedKey = key?.toString() ?: return@mapNotNull null
                    val normalizedValue = value?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    normalizedKey to normalizedValue
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun firstValue(values: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> values[key]?.takeIf { it.isNotBlank() } }
    }

    private companion object {
        const val JSON_CONTENT_TYPE = "json"
        const val MAX_BODY_CHARS = 4_096
        const val USERNAME_MAX_LENGTH = 64
        const val DEFAULT_HONEYPOT_USERNAME = "honeypot"
        const val LOGIN_FAILED_MESSAGE = "用户名或密码错误"
    }
}
