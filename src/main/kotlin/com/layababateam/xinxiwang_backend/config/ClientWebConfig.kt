package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.middleware.BotApiAuthInterceptor
import com.layababateam.xinxiwang_backend.middleware.ClientAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ClientWebConfig(
    private val clientAuthInterceptor: ClientAuthInterceptor,
    private val botApiAuthInterceptor: BotApiAuthInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(clientAuthInterceptor)
            .addPathPatterns(
                "/api/upload/**",
                "/api/friend/**",
                "/api/moments/**",
                "/api/cards/**",
                "/api/wallet/**",
                "/api/v1/stickers/**",
                "/api/conversation/**",
                "/api/user/**",
                "/api/me/**",
                "/api/report",
                "/api/feedback",
                "/api/invite/**",
                "/api/auth/delete-account",
                "/api/auth/change-password",
                "/api/asr/**",
                "/api/trtc/**",
                "/api/push/**",
                "/api/meeting/**",
                "/api/media/keys/**",
            )
            .excludePathPatterns(
                "/api/wallet/webhook/**",
                "/api/invite/user/**",
                "/api/invite/group/**",
            )

        registry.addInterceptor(botApiAuthInterceptor)
            .addPathPatterns("/api/bot/**")
    }
}
