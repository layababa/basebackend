package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.service.SalesmartlyExternalApiResponse
import com.layababateam.xinxiwang_backend.service.SalesmartlyExternalApiService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections

@RestController
class SalesmartlyExternalApiController(
    private val service: SalesmartlyExternalApiService,
    private val objectMapper: JsonMapper,
) {
    @RequestMapping("/api/v2/**")
    fun handle(request: HttpServletRequest): ResponseEntity<SalesmartlyExternalApiResponse> {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        val response = service.handle(
            method = request.method,
            path = path,
            queryParams = parseQuery(request.queryString),
            bodyParams = parseBody(request),
            headers = Collections.list(request.headerNames).associateWith { request.getHeader(it) },
        )
        return ResponseEntity.status(response.httpStatus).body(response)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBody(request: HttpServletRequest): Map<String, Any?> {
        val contentType = request.contentType.orEmpty().lowercase()
        if ("application/json" in contentType) {
            val raw = request.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            if (raw.isBlank()) return emptyMap()
            return (objectMapper.readValue(raw, Map::class.java) as Map<*, *>)
                .mapKeys { it.key.toString() }
        }
        if ("application/x-www-form-urlencoded" in contentType || "multipart/form-data" in contentType) {
            return request.parameterMap.mapValues { (_, values) -> values.lastOrNull().orEmpty() }
        }
        return emptyMap()
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&')
            .filter { it.isNotBlank() }
            .associate { item ->
                val key = item.substringBefore('=')
                val value = item.substringAfter('=', "")
                decode(key) to decode(value)
            }
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
