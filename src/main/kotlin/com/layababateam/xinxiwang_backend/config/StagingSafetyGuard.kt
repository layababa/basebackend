package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.service.ClientAuthRefreshPolicy
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.SmartLifecycle
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.config.ScheduledTask
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class StagingSafetyPolicy(
    @Value("\${app.environment:\${sentry.environment:production}}") environment: String,
    @Value("\${xinxiwang.staging.write-protection.enabled:#{null}}") configuredWriteProtection: Boolean?,
    @Value("\${xinxiwang.staging.allow-business-ws:false}") private val allowBusinessWs: Boolean,
    @Value("\${xinxiwang.staging.allow-scheduled-tasks:false}") val allowScheduledTasks: Boolean,
    @Value("\${xinxiwang.staging.allow-rabbit-listeners:false}") val allowRabbitListeners: Boolean,
    @Value("\${xinxiwang.node.id:node-default}") val nodeId: String,
    @Value("\${spring.rabbitmq.virtual-host:/}") val rabbitVirtualHost: String,
) : ClientAuthRefreshPolicy {
    val staging: Boolean = environment.trim().equals("staging", ignoreCase = true)
    val writeProtectionEnabled: Boolean = staging && (configuredWriteProtection ?: false)
    override val refreshTokenTtlOnClientAuth: Boolean
        get() = !writeProtectionEnabled

    fun allowHttp(method: String, path: String): Boolean {
        if (!writeProtectionEnabled) return true
        if (method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true) || method.equals("OPTIONS", ignoreCase = true)) {
            return true
        }

        val normalized = path.substringBefore('?')
        return HTTP_WRITE_ALLOWLIST.any { pattern -> pattern.matches(normalized) }
    }

    fun allowWebSocketType(type: String): Boolean {
        if (!writeProtectionEnabled || allowBusinessWs) return true
        return type in WS_ALLOWLIST
    }

    companion object {
        private val HTTP_WRITE_ALLOWLIST = setOf(
            Regex("^/api/admin/auth/(login|verify-2fa|refresh)$"),
            Regex("^/api/admin/auth/2fa/setup/confirm$"),
        )
        private val WS_ALLOWLIST = setOf("auth", "ping", "pong")
    }
}

@Component
class StagingDeploymentSafetyGuard(
    private val policy: StagingSafetyPolicy,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(StagingDeploymentSafetyGuard::class.java)
    private var running = false

    override fun start() {
        validate()
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = Int.MIN_VALUE

    private fun validate() {
        if (!policy.staging) return
        val errors = mutableListOf<String>()
        if (!policy.nodeId.startsWith("staging-")) {
            errors += "staging xinxiwang.node.id must start with 'staging-' (actual '${policy.nodeId}')"
        }
        if (policy.rabbitVirtualHost == "/" || policy.rabbitVirtualHost.equals("xianyun", ignoreCase = true)) {
            errors += "staging spring.rabbitmq.virtual-host must not be production/default (actual '${policy.rabbitVirtualHost}')"
        }
        if (errors.isNotEmpty()) {
            val message = errors.joinToString("; ")
            log.error("Staging deployment safety validation failed: {}", message)
            throw IllegalStateException("Staging deployment safety validation failed: $message")
        }
        log.info(
            "Staging deployment safety validation passed nodeId={} rabbitVirtualHost={} writeProtection={} rabbitListenersAllowed={}",
            policy.nodeId,
            policy.rabbitVirtualHost,
            policy.writeProtectionEnabled,
            policy.allowRabbitListeners,
        )
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class StagingWriteProtectionFilter(
    private val policy: StagingSafetyPolicy,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.servletPath?.takeIf { it.isNotBlank() } ?: request.requestURI.orEmpty()
        if (policy.allowHttp(request.method, path)) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"success":false,"message":"staging write protection enabled"}""")
    }
}

@Component
class StagingScheduledTaskGuard(
    private val policy: StagingSafetyPolicy,
    private val scheduledTaskHolders: List<ScheduledTaskHolder>,
) : ApplicationListener<ContextRefreshedEvent>, Ordered {
    private val log = LoggerFactory.getLogger(StagingScheduledTaskGuard::class.java)

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        if (!policy.writeProtectionEnabled || policy.allowScheduledTasks) return
        scheduledTaskHolders.flatMap { it.scheduledTasks }
            .distinct()
            .forEach(ScheduledTask::cancel)
        log.warn("Cancelled scheduled tasks while staging write protection is enabled")
    }
}
