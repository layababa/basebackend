package com.layababateam.xinxiwang_backend.config

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig(
    private val connectionFactory: CachingConnectionFactory,
) {
    private val log = LoggerFactory.getLogger(RabbitConfig::class.java)

    @Bean
    fun rabbitConnectionVerifier() = ApplicationRunner {
        val connection = connectionFactory.createConnection()
        log.info(
            "RabbitMQ connected: {}:{} (isOpen={})",
            connectionFactory.host,
            connectionFactory.port,
            connection.isOpen,
        )
    }
}
