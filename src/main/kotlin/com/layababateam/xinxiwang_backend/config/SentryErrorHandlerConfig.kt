package com.layababateam.xinxiwang_backend.config

import io.sentry.Sentry
import io.sentry.SentryLevel
import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import java.lang.reflect.Method

@Configuration
@ConditionalOnClass(name = ["io.sentry.Sentry"])
class SentryErrorHandlerConfig : AsyncConfigurer {

    private val logger = LoggerFactory.getLogger(SentryErrorHandlerConfig::class.java)

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex: Throwable, method: Method, params: Array<out Any?> ->
            logger.error("Async method [${method.declaringClass.simpleName}.${method.name}] failed: ${ex.message}", ex)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setTag("error_source", "async_method")
                scope.setTag("async_class", method.declaringClass.simpleName)
                scope.setTag("async_method", method.name)
                scope.setExtra("parameters", params.map { it.toString() }.joinToString())
                Sentry.captureException(ex)
            }
        }
    }
}
