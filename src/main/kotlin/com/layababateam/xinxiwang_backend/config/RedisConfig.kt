package com.layababateam.xinxiwang_backend.config

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder
import io.lettuce.core.metrics.MicrometerOptions
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DnsResolvers
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {
    @Bean(destroyMethod = "shutdown")
    fun lettuceClientResources(meterRegistry: MeterRegistry): ClientResources {
        val options = MicrometerOptions.builder()
            .enable()
            .build()
        return DefaultClientResources.builder()
            .dnsResolver(DnsResolvers.JVM_DEFAULT)
            .commandLatencyRecorder(MicrometerCommandLatencyRecorder(meterRegistry, options))
            .build()
    }

    @Bean
    fun stringRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        @Value("\${app.environment:\${sentry.environment:production}}") appEnvironment: String,
        @Value("\${xinxiwang.redis.key-prefix:}") configuredKeyPrefix: String,
    ): StringRedisTemplate {
        val stringSerializer = StringRedisSerializer()
        val keyPrefix = InfrastructureNamespaces.effectiveRedisKeyPrefix(appEnvironment, configuredKeyPrefix)
        val keySerializer = if (keyPrefix.isBlank()) {
            stringSerializer
        } else {
            PrefixingStringRedisSerializer(keyPrefix)
        }

        return StringRedisTemplate().apply {
            setConnectionFactory(connectionFactory)
            this.keySerializer = keySerializer
            this.valueSerializer = stringSerializer
            this.hashKeySerializer = stringSerializer
            this.hashValueSerializer = stringSerializer
            afterPropertiesSet()
        }
    }
}

private class PrefixingStringRedisSerializer(
    private val prefix: String,
    private val delegate: StringRedisSerializer = StringRedisSerializer(),
) : RedisSerializer<String> {
    override fun serialize(value: String?): ByteArray {
        if (value == null) return ByteArray(0)
        val prefixed = if (value.startsWith(prefix)) value else "$prefix$value"
        return delegate.serialize(prefixed)
    }

    override fun deserialize(bytes: ByteArray?): String? {
        val value = delegate.deserialize(bytes) ?: return null
        return if (value.startsWith(prefix)) value.removePrefix(prefix) else value
    }
}
