package com.layababateam.xinxiwang_backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

// 管理端口 (9090) 的安全配置：Actuator 端点需要 HTTP Basic 认证。
// securityMatcher 限定只匹配 /actuator/** 路径，避免与主应用 filter chain 冲突。
@Configuration(proxyBeanMethods = false)
class ManagementSecurityConfig {

    @Bean
    @Order(1)
    fun managementSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(Customizer.withDefaults())
            .csrf { it.disable() }
        return http.build()
    }
}
