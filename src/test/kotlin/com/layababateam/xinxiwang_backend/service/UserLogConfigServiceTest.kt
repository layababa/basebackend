package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.UserLogConfig
import com.layababateam.xinxiwang_backend.repository.UserLogConfigRepository
import org.springframework.data.mongodb.core.MongoTemplate
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserLogConfigServiceTest {
    @Test
    fun `missing user config defaults critical logs to enabled`() {
        val service = UserLogConfigService(
            repository = repositoryReturning(null),
            mongoTemplate = uninitialized(MongoTemplate::class.java),
        )

        val config = service.getOrDefault("u1")

        assertEquals("u1", config.userId)
        assertTrue(config.criticalLogEnabled)
        assertEquals(0, config.revision)
    }

    @Test
    fun `update rejects stale expected revision`() {
        val service = UserLogConfigService(
            repository = repositoryReturning(UserLogConfig(userId = "u1", criticalLogEnabled = true, revision = 3)),
            mongoTemplate = uninitialized(MongoTemplate::class.java),
        )

        val result = service.update("u1", criticalLogEnabled = false, expectedRevision = 2, updatedBy = "admin1")

        assertFalse(result.updated)
        assertEquals(3, result.config.revision)
        assertTrue(result.config.criticalLogEnabled)
    }

    private fun repositoryReturning(config: UserLogConfig?): UserLogConfigRepository {
        return Proxy.newProxyInstance(
            UserLogConfigRepository::class.java.classLoader,
            arrayOf(UserLogConfigRepository::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "findByUserId" -> config?.takeIf { it.userId == args?.getOrNull(0) }
                    "toString" -> "test-user-log-config-repository"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> error("Unexpected repository call in this test: ${method.name}")
                }
            },
        ) as UserLogConfigRepository
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(type: Class<T>): T {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(type) as T
    }
}
