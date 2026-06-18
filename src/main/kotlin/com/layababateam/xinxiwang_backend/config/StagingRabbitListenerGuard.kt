package com.layababateam.xinxiwang_backend.config

import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component

@Component
class StagingRabbitListenerGuard(
    private val policy: StagingSafetyPolicy,
) : BeanPostProcessor {
    private val log = LoggerFactory.getLogger(StagingRabbitListenerGuard::class.java)

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (!policy.writeProtectionEnabled || policy.allowRabbitListeners) return bean
        if (bean is AbstractRabbitListenerContainerFactory<*>) {
            bean.setAutoStartup(false)
            log.warn("Disabled Rabbit listener auto-startup for bean '{}' while staging write protection is enabled", beanName)
        }
        return bean
    }
}
