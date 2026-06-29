package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.AdminAuditLog
import com.layababateam.xinxiwang_backend.repository.AdminAuditLogRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class AuditLogServiceTest {
    @Test
    fun `log persists admin audit event`() {
        val repository = RecordingAuditLogRepository()
        val service = AuditLogService(repository.proxy)

        service.log(
            adminId = "admin-1",
            adminUsername = "root",
            action = "BAN_USER",
            targetType = "user",
            targetId = "u1",
            details = "reason",
            ipAddress = "127.0.0.1",
        )

        val saved = repository.saved.single()
        assertEquals("admin-1", saved.adminId)
        assertEquals("root", saved.adminUsername)
        assertEquals("BAN_USER", saved.action)
        assertEquals("user", saved.targetType)
        assertEquals("u1", saved.targetId)
        assertEquals("reason", saved.details)
        assertEquals("127.0.0.1", saved.ipAddress)
    }

    @Test
    fun `queries logs through repository`() {
        val repository = RecordingAuditLogRepository()
        val service = AuditLogService(repository.proxy)
        val pageable = PageRequest.of(0, 20)

        service.getLogs(pageable)
        service.getLogsByAdmin("admin-1", pageable)

        assertEquals(listOf("findAllByOrderByCreatedAtDesc", "findByAdminId:admin-1"), repository.calls)
    }

    private class RecordingAuditLogRepository {
        val saved = mutableListOf<AdminAuditLog>()
        val calls = mutableListOf<String>()

        val proxy: AdminAuditLogRepository = Proxy.newProxyInstance(
            AdminAuditLogRepository::class.java.classLoader,
            arrayOf(AdminAuditLogRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "save" -> {
                    @Suppress("UNCHECKED_CAST")
                    saved += args?.first() as AdminAuditLog
                    args.first()
                }
                "findAllByOrderByCreatedAtDesc" -> {
                    calls += "findAllByOrderByCreatedAtDesc"
                    PageImpl(emptyList<AdminAuditLog>())
                }
                "findByAdminId" -> {
                    calls += "findByAdminId:${args?.first()}"
                    PageImpl(emptyList<AdminAuditLog>())
                }
                "toString" -> "RecordingAuditLogRepository"
                else -> error("Unsupported repository method: ${method.name}")
            }
        } as AdminAuditLogRepository
    }
}
