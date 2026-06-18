package com.layababateam.xinxiwang_backend.config

import io.sentry.Sentry
import io.sentry.SentryLevel
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component

@Component
class SentryRabbitAdvice : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is AbstractRabbitListenerContainerFactory<*>) {
            bean.setAdviceChain(SentryConsumerInterceptor())
        }
        return bean
    }

    private class SentryConsumerInterceptor : MethodInterceptor {
        override fun invoke(invocation: MethodInvocation): Any? {
            val consumerClass = invocation.`this`?.javaClass?.simpleName ?: "Unknown"
            try {
                return invocation.proceed()
            } catch (e: Throwable) {
                Sentry.withScope { scope ->
                    scope.setTag("layer", "rabbitmq-consumer")
                    scope.setTag("consumer", consumerClass)
                    scope.level = SentryLevel.ERROR
                    val args = invocation.arguments
                    if (args.isNotEmpty()) {
                        val payload = args[0]
                        val summary = when (payload) {
                            is Map<*, *> -> payload.entries.take(8).joinToString(", ") { "${it.key}=${it.value}" }
                            else -> payload?.toString()?.take(500) ?: "null"
                        }
                        scope.setContexts(
                            "rabbitmq",
                            mapOf(
                                "consumer" to consumerClass,
                                "payloadSummary" to summary,
                            ),
                        )
                    }
                    Sentry.captureException(e)
                }
                throw e
            }
        }
    }
}
