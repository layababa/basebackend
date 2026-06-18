package com.layababateam.xinxiwang_backend.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
open class DeploymentSafetyGuard(
    @Value("\${app.environment:\${sentry.environment:production}}") private val appEnvironment: String,
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
    @Value("\${xinxiwang.redis.key-prefix:}") private val redisKeyPrefix: String,
    @Value("\${xinxiwang.rabbit.name-prefix:}") private val rabbitNamePrefix: String,
    @Value("\${xinxiwang.mongo.collection-prefix:}") private val mongoCollectionPrefix: String,
    @Value("\${spring.rabbitmq.virtual-host:/}") private val rabbitVirtualHost: String,
    @Value("\${xinxiwang.node.id}") private val nodeId: String,
) {
    private val log = LoggerFactory.getLogger(DeploymentSafetyGuard::class.java)

    @PostConstruct
    fun validate() {
        val env = appEnvironment.trim().lowercase()
        val effectiveRedisPrefix = InfrastructureNamespaces.effectiveRedisKeyPrefix(env, redisKeyPrefix)
        val effectiveRabbitPrefix = InfrastructureNamespaces.effectiveRabbitNamePrefix(env, rabbitNamePrefix)
        val effectiveMongoPrefix = InfrastructureNamespaces.effectiveMongoCollectionPrefix(env, mongoCollectionPrefix)
        val errors = mutableListOf<String>()

        when (env) {
            "staging" -> validateStaging(effectiveRedisPrefix, effectiveRabbitPrefix, effectiveMongoPrefix, errors)
            "production", "prod" -> validateProduction(effectiveRedisPrefix, effectiveRabbitPrefix, effectiveMongoPrefix, errors)
            "local", "dev", "development", "test" -> Unit
            else -> errors += "app.environment/sentry.environment must be one of staging, production, local/dev/test; got '$appEnvironment'"
        }

        if (errors.isNotEmpty()) {
            val message = errors.joinToString("; ")
            log.error("Deployment safety validation failed: {}", message)
            throw IllegalStateException("Deployment safety validation failed: $message")
        }

        log.info(
            "Deployment safety validation passed env={} nodeId={} mongoDb={} mongoCollectionPrefix='{}' redisPrefix='{}' rabbitNamePrefix='{}' rabbitVhost={}",
            env,
            nodeId,
            extractMongoDatabase(mongoUri),
            effectiveMongoPrefix,
            effectiveRedisPrefix,
            effectiveRabbitPrefix,
            rabbitVirtualHost,
        )
    }

    private fun validateStaging(
        effectiveRedisPrefix: String,
        effectiveRabbitPrefix: String,
        effectiveMongoPrefix: String,
        errors: MutableList<String>,
    ) {
        if (nodeId.isBlank() || nodeId == "node-default" || nodeId == "node-1" || !nodeId.startsWith("staging-")) {
            errors += "staging XINXIWANG_NODE_ID or LINKA_NODE_ID must be explicit and start with 'staging-' (actual '$nodeId')"
        }
        if (!effectiveRedisPrefix.startsWith("staging:")) {
            errors += "staging Redis namespace must start with 'staging:' (actual '$effectiveRedisPrefix')"
        }
        if (!effectiveRabbitPrefix.startsWith("staging.")) {
            errors += "staging Rabbit namespace must start with 'staging.' (actual '$effectiveRabbitPrefix')"
        }
        if (!effectiveMongoPrefix.startsWith("staging_")) {
            errors += "staging Mongo collection namespace must start with 'staging_' (actual '$effectiveMongoPrefix')"
        }
    }

    private fun validateProduction(
        effectiveRedisPrefix: String,
        effectiveRabbitPrefix: String,
        effectiveMongoPrefix: String,
        errors: MutableList<String>,
    ) {
        if (nodeId.startsWith("staging-")) {
            errors += "production XINXIWANG_NODE_ID must not start with 'staging-' (actual '$nodeId')"
        }
        if (effectiveRedisPrefix.startsWith("staging:")) {
            errors += "production Redis namespace must not start with 'staging:'"
        }
        if (effectiveRabbitPrefix.startsWith("staging.")) {
            errors += "production Rabbit namespace must not start with 'staging.'"
        }
        if (effectiveMongoPrefix.startsWith("staging_")) {
            errors += "production Mongo collection namespace must not start with 'staging_'"
        }
    }

    private fun extractMongoDatabase(uri: String): String? {
        val afterScheme = uri.substringAfter("://", missingDelimiterValue = uri)
        val path = afterScheme.substringAfter("/", missingDelimiterValue = "")
        return path
            .substringBefore("?")
            .substringBefore("/")
            .takeIf { it.isNotBlank() }
    }
}
