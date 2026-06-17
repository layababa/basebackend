package com.layababateam.xinxiwang_backend.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(PushDaConfig::class)
@ConfigurationProperties(prefix = "pushda")
class PushDaConfig {
    var enabled: Boolean = false
    var appName: String = ""
    var appSecret: String = ""
    var baseUrl: String = DEFAULT_BASE_URL

    @Bean
    @ConditionalOnMissingBean(name = ["pushDaRestTemplate"])
    fun pushDaRestTemplate(): RestTemplate = RestTemplate()

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.pushda.xin"
    }
}
