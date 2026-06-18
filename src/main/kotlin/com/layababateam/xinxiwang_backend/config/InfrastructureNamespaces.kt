package com.layababateam.xinxiwang_backend.config

object InfrastructureNamespaces {
    fun isStaging(environment: String): Boolean =
        environment.trim().equals("staging", ignoreCase = true)

    fun effectiveRedisKeyPrefix(environment: String, configuredPrefix: String): String =
        configuredPrefix.trim().ifBlank { if (isStaging(environment)) "staging:" else "" }

    fun effectiveRabbitNamePrefix(environment: String, configuredPrefix: String): String =
        configuredPrefix.trim().ifBlank { if (isStaging(environment)) "staging." else "" }

    fun effectiveMongoCollectionPrefix(environment: String, configuredPrefix: String): String =
        configuredPrefix.trim().ifBlank { if (isStaging(environment)) "staging_" else "" }

    fun prefixed(prefix: String, name: String): String =
        if (prefix.isBlank() || name.isBlank() || name.startsWith(prefix)) name else "$prefix$name"

    fun unprefixed(prefix: String, name: String): String =
        if (prefix.isBlank() || !name.startsWith(prefix)) name else name.removePrefix(prefix)
}
