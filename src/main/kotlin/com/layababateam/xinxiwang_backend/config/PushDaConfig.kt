package com.layababateam.xinxiwang_backend.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
@EnableConfigurationProperties(PushDaConfig::class)
@ConfigurationProperties(prefix = "pushda")
class PushDaConfig {
    var enabled: Boolean = false
    var appName: String = ""
    var appSecret: String = ""
    var baseUrl: String = DEFAULT_BASE_URL
    var connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS
    var readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS

    @Bean
    @ConditionalOnMissingBean(name = ["pushDaRestTemplate"])
    fun pushDaRestTemplate(): RestTemplate =
        RestTemplate(timeoutRequestFactory(connectTimeoutMs, readTimeoutMs))

    companion object {
        fun timeoutRequestFactory(connectTimeoutMs: Long, readTimeoutMs: Long): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(connectTimeoutMs.coerceAtLeast(1)))
                setReadTimeout(Duration.ofMillis(readTimeoutMs.coerceAtLeast(1)))
            }

        const val DEFAULT_BASE_URL = "https://api.pushda.xin"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 3_000L
        const val DEFAULT_READ_TIMEOUT_MS = 5_000L
    }
}
