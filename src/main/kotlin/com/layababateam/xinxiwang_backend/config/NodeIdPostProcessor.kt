package com.layababateam.xinxiwang_backend.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.util.UUID

class NodeIdPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        if (hasExplicitNodeId(environment) || !environment.getProperty("XINXIWANG_NODE_ID").isNullOrBlank()) {
            return
        }

        val staging = environment.getProperty("app.environment", environment.getProperty("sentry.environment", "production"))
            .trim()
            .equals("staging", ignoreCase = true)
        if (staging) {
            val legacyNodeId = environment.getProperty("LINKA_NODE_ID")?.takeIf { it.isNotBlank() }
            if (legacyNodeId != null) {
                environment.propertySources.addFirst(
                    MapPropertySource("dynamicNodeId", mapOf("xinxiwang.node.id" to legacyNodeId)),
                )
                return
            }
        }

        val existing = environment.getProperty("xinxiwang.node.id")
        if (!existing.isNullOrBlank()) return

        val nodeId = System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
            ?: "node-${UUID.randomUUID().toString().substring(0, 8)}"

        environment.propertySources.addFirst(
            MapPropertySource("dynamicNodeId", mapOf("xinxiwang.node.id" to nodeId)),
        )
    }

    private fun hasExplicitNodeId(environment: ConfigurableEnvironment): Boolean =
        environment.propertySources.any { source ->
            source.name != "configurationProperties" &&
                !source.name.startsWith("Config resource") &&
                source.name != "dynamicNodeId" &&
                source.containsProperty("xinxiwang.node.id")
        }
}
