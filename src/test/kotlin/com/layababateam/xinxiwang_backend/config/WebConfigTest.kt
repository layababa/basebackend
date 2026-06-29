package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.middleware.AdminAuthInterceptor
import com.layababateam.xinxiwang_backend.middleware.BotApiAuthInterceptor
import com.layababateam.xinxiwang_backend.middleware.ClientAuthInterceptor
import com.layababateam.xinxiwang_backend.service.AdminAuthContext
import com.layababateam.xinxiwang_backend.service.AdminRequestAuthPort
import com.layababateam.xinxiwang_backend.service.AuthTokenResolver
import com.layababateam.xinxiwang_backend.service.BotApiCredentialResolver
import com.layababateam.xinxiwang_backend.service.ClientAuthRefreshPolicy
import kotlin.test.Test
import org.springframework.web.servlet.config.annotation.InterceptorRegistry

class WebConfigTest {
    @Test
    fun `admin web config registers admin interceptor`() {
        val config = AdminWebConfig(
            AdminAuthInterceptor(
                object : AdminRequestAuthPort {
                    override fun authenticateAdminRequest(token: String): AdminAuthContext? = null
                },
            ),
        )

        config.addInterceptors(InterceptorRegistry())
    }

    @Test
    fun `client web config registers client and bot interceptors`() {
        val config = ClientWebConfig(
            ClientAuthInterceptor(
                object : AuthTokenResolver {
                    override fun resolveUserId(authHeader: String?, refreshTtl: Boolean): String? = null
                },
                object : ClientAuthRefreshPolicy {},
            ),
            BotApiAuthInterceptor(
                object : BotApiCredentialResolver {
                    override fun resolveBotUserId(apiKey: String): String? = null
                },
            ),
        )

        config.addInterceptors(InterceptorRegistry())
    }
}
