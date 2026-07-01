package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceApiResponse
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceCreateSessionRequest
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceTextMessageRequest
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.service.CustomerServiceExternalApiAuthService
import com.layababateam.xinxiwang_backend.service.CustomerServiceExternalApiService
import com.layababateam.xinxiwang_backend.service.ExternalCustomerServiceApiException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@RestController
@RequestMapping("/api/external/customer-service")
class CustomerServiceExternalApiController(
    private val authService: CustomerServiceExternalApiAuthService,
    private val service: CustomerServiceExternalApiService,
    private val objectMapper: JsonMapper,
) {
    @PostMapping("/sessions")
    fun createSession(
        request: HttpServletRequest,
        @RequestBody rawBody: String,
    ): ResponseEntity<ExternalCustomerServiceApiResponse<*>> =
        handle(request, rawBody) { credential ->
            service.createSession(
                credential,
                objectMapper.readValue(rawBody, ExternalCustomerServiceCreateSessionRequest::class.java),
            )
        }

    @PostMapping("/sessions/{sessionId}/messages")
    fun sendMessage(
        request: HttpServletRequest,
        @PathVariable sessionId: String,
        @RequestBody rawBody: String,
    ): ResponseEntity<ExternalCustomerServiceApiResponse<*>> =
        handle(request, rawBody) { credential ->
            service.sendMessage(
                credential,
                sessionId,
                objectMapper.readValue(rawBody, ExternalCustomerServiceTextMessageRequest::class.java),
            )
        }

    @GetMapping("/sessions/{sessionId}/messages")
    fun messages(
        request: HttpServletRequest,
        @PathVariable sessionId: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ExternalCustomerServiceApiResponse<*>> =
        handle(request, "") { credential ->
            service.messages(credential, sessionId, after, size)
        }

    private fun handle(
        request: HttpServletRequest,
        rawBody: String,
        block: (com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential) -> Any,
    ): ResponseEntity<ExternalCustomerServiceApiResponse<*>> {
        val requestId = request.getHeader("X-Request-Id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return try {
            val credential = authService.authenticate(request, rawBody)
            ResponseEntity.ok(
                ExternalCustomerServiceApiResponse(
                    code = 0,
                    data = block(credential),
                    msg = "OK",
                    requestId = requestId,
                ),
            )
        } catch (e: ExternalCustomerServiceApiException) {
            ResponseEntity.status(e.status).body(error(e.externalCode, e.message, requestId))
        } catch (e: NotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(211, e.message ?: "not found", requestId))
        } catch (e: ForbiddenException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(103, e.message ?: "forbidden", requestId))
        } catch (e: BusinessException) {
            ResponseEntity.badRequest().body(error(400, e.message ?: "bad request", requestId))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(500, e.message ?: "system error", requestId))
        }
    }

    private fun error(code: Int, msg: String, requestId: String): ExternalCustomerServiceApiResponse<Nothing> =
        ExternalCustomerServiceApiResponse(
            code = code,
            data = null,
            msg = msg,
            requestId = requestId,
        )
}
