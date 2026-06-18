package com.layababateam.xinxiwang_backend.config

import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate

class NamespacedRabbitTemplate(
    private val rabbitNames: RabbitNames
) : RabbitTemplate() {
    override fun send(
        exchange: String?,
        routingKey: String?,
        message: Message,
        correlationData: CorrelationData?
    ) {
        super.send(
            namespaceExchange(exchange),
            namespaceRoutingKey(exchange, routingKey),
            message,
            correlationData,
        )
    }

    private fun namespaceExchange(exchange: String?): String? =
        if (exchange.isNullOrBlank()) exchange else rabbitNames.name(exchange)

    private fun namespaceRoutingKey(exchange: String?, routingKey: String?): String? =
        if (exchange.isNullOrBlank() && !routingKey.isNullOrBlank()) {
            rabbitNames.name(routingKey)
        } else {
            routingKey
        }
}
