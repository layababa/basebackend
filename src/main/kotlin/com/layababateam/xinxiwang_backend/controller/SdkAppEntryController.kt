package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.config.RequireAdmin
import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.middleware.AdminAuthInterceptor
import com.layababateam.xinxiwang_backend.middleware.ClientAuthInterceptor
import com.layababateam.xinxiwang_backend.service.AppEntryPort
import com.layababateam.xinxiwang_backend.service.SdkAppEntryRequest
import com.layababateam.xinxiwang_backend.service.SdkAppEntryToggleRequest
import com.layababateam.xinxiwang_backend.service.SdkH5SessionExchangeRequest
import com.layababateam.xinxiwang_backend.service.SdkH5TicketRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnBean(AppEntryPort::class)
@RequestMapping("/api/admin/app-entries")
class SdkAdminAppEntryController(
    private val appEntryPort: AppEntryPort,
) {
    @RequireAdmin("ADMIN")
    @GetMapping
    fun list(
        @RequestParam(required = false) placement: String?,
        @RequestParam(required = false) enabled: Boolean?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(appEntryPort.listAdminEntries(placement, enabled)))

    @RequireAdmin("SUPER_ADMIN")
    @PostMapping
    fun create(
        request: HttpServletRequest,
        @RequestBody body: SdkAppEntryRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(appEntryPort.saveEntry(body, null, adminName(request))))

    @RequireAdmin("SUPER_ADMIN")
    @PutMapping("/{id}")
    fun update(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: SdkAppEntryRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(appEntryPort.saveEntry(body, id, adminName(request))))

    @RequireAdmin("SUPER_ADMIN")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        appEntryPort.deleteEntry(id)
        return ResponseEntity.ok(ApiResponse.ok("已删除"))
    }

    @RequireAdmin("SUPER_ADMIN")
    @PostMapping("/{id}/toggle")
    fun toggle(
        request: HttpServletRequest,
        @PathVariable id: String,
        @RequestBody body: SdkAppEntryToggleRequest,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(appEntryPort.toggleEntry(id, body.enabled, adminName(request))))

    private fun adminName(request: HttpServletRequest): String =
        request.getAttribute(AdminAuthInterceptor.ADMIN_USERNAME_ATTR) as? String ?: "admin"
}

@RestController
@ConditionalOnBean(AppEntryPort::class)
@RequestMapping("/api/open/app-entries")
class SdkOpenAppEntryController(
    private val appEntryPort: AppEntryPort,
) {
    @GetMapping
    fun list(
        @RequestParam placement: String,
        @RequestParam platform: String,
        @RequestParam(required = false) version: String?,
    ): ResponseEntity<ApiResponse<*>> =
        ResponseEntity.ok(ApiResponse.ok(appEntryPort.listOpenEntries(placement, platform, version)))
}

@RestController
@ConditionalOnBean(AppEntryPort::class)
@RequestMapping("/api/h5")
class SdkH5TicketController(
    private val appEntryPort: AppEntryPort,
) {
    @PostMapping("/ticket")
    fun createTicket(
        request: HttpServletRequest,
        @RequestBody body: SdkH5TicketRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute(ClientAuthInterceptor.USER_ID_ATTR) as String
        val platform = request.getAttribute(ClientAuthInterceptor.CLIENT_PLATFORM_ATTR) as? String
        val clientVersion = request.getHeader("X-App-Version")
        return ResponseEntity.ok(
            ApiResponse.ok(
                appEntryPort.createH5Ticket(userId, body.entryKey, platform, clientVersion, body.node),
            ),
        )
    }

    @PostMapping("/session/exchange")
    fun exchange(@RequestBody body: SdkH5SessionExchangeRequest): ResponseEntity<ApiResponse<*>> {
        val session = appEntryPort.exchangeH5Ticket(body.ticket)
            ?: return ResponseEntity.status(401).body(ApiResponse.error<Any>("ticket无效或已过期"))
        return ResponseEntity.ok(ApiResponse.ok(session))
    }
}
