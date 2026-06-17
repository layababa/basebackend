package com.layababateam.xinxiwang_backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class WebSecurityConfig {

    /**
     * 主应用端口 (8080) 的安全配置：
     * 放行所有请求，认证由自定义拦截器 (ClientAuthInterceptor / AdminAuthInterceptor) 处理。
     * @Order(2) 确保在 ManagementSecurityConfig(@Order(1)) 之后匹配。
     */
    @Bean
    @Order(2)
    fun mainSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors {}
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
        return http.build()
    }
}
