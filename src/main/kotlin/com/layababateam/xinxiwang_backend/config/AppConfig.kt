package com.layababateam.xinxiwang_backend.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerKotlinModule()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
    }

    /**
     * 确保 Spring MVC 的 @RequestBody/@ResponseBody 转换器使用的 ObjectMapper 也注册了 KotlinModule。
     * 仅靠上面的 @Bean objectMapper() 在某些构建/部署下不会被 MappingJackson2HttpMessageConverter 采用，
     * 导致 Kotlin data class（如 LoginRequest）反序列化抛 InvalidDefinitionException「no Creators」→ 500。
     * Boot 会把该 customizer 应用到 MVC 转换器使用的那个 mapper，从根本上消灭该问题。
     */
    @Bean
    fun kotlinModuleCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.modulesToInstall(KotlinModule.Builder().build())
            builder.failOnUnknownProperties(false)
            builder.serializationInclusion(JsonInclude.Include.NON_NULL)
        }
}
