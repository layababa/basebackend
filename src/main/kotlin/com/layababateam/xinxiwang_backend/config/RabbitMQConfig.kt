package com.layababateam.xinxiwang_backend.config

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateConfigurer
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import tools.jackson.databind.json.JsonMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.interceptor.RetryOperationsInterceptor
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate

@Configuration
class RabbitMQConfig(
    @Value("\${xinxiwang.node.id:node-default}") private val nodeId: String,
    private val rabbitNames: RabbitNames,
) {
    companion object {
        // --- Exchanges ---
        const val ROUTE_EXCHANGE = "xinxiwang.route"
        const val EVENT_GROUP_EXCHANGE = "xinxiwang.event.group"
        const val EVENT_FRIEND_EXCHANGE = "xinxiwang.event.friend"
        const val EVENT_CONVERSATION_EXCHANGE = "xinxiwang.event.conversation"

        // --- Queues ---
        const val MESSAGE_PERSIST_QUEUE = "xinxiwang.message.persist"
        const val MESSAGE_PERSIST_DLQ = "xinxiwang.message.persist.dlq"
        const val ACK_TIMEOUT_QUEUE = "xinxiwang.ack.timeout"

        // --- Async processing queues ---
        const val MESSAGE_RECALL_QUEUE = "xinxiwang.message.recall"
        const val MESSAGE_DELETE_QUEUE = "xinxiwang.message.delete"
        const val FRIEND_ACCEPT_QUEUE = "xinxiwang.friend.accept"
        const val MOMENT_LIKE_QUEUE = "xinxiwang.moment.like"
        const val ACCOUNT_DELETE_QUEUE = "xinxiwang.account.delete"
        const val PROFILE_UPDATE_QUEUE = "xinxiwang.profile.update"
        const val WALLET_TRANSACTION_QUEUE = "xinxiwang.wallet.transaction"
        const val REDPACKET_CLAIM_QUEUE = "xinxiwang.redpacket.claim"
        const val WALLET_TRANSACTION_DLQ = "xinxiwang.wallet.transaction.dlq"
        const val REDPACKET_CLAIM_DLQ = "xinxiwang.redpacket.claim.dlq"
        const val BROADCAST_QUEUE = "xinxiwang.broadcast"

        // --- DLQs for async processing queues ---
        const val ACK_TIMEOUT_DLQ = "xinxiwang.ack.timeout.dlq"
        const val MESSAGE_RECALL_DLQ = "xinxiwang.message.recall.dlq"
        const val MESSAGE_DELETE_DLQ = "xinxiwang.message.delete.dlq"
        const val FRIEND_ACCEPT_DLQ = "xinxiwang.friend.accept.dlq"
        const val MOMENT_LIKE_DLQ = "xinxiwang.moment.like.dlq"
        const val ACCOUNT_DELETE_DLQ = "xinxiwang.account.delete.dlq"
        const val PROFILE_UPDATE_DLQ = "xinxiwang.profile.update.dlq"
        const val BROADCAST_DLQ = "xinxiwang.broadcast.dlq"

        const val APNS_PUSH_QUEUE = "xinxiwang.apns.push"
        const val APNS_PUSH_DLQ = "xinxiwang.apns.push.dlq"
        const val GROUP_MESSAGE_DISPATCH_EXCHANGE = "xinxiwang.group.message.dispatch.exchange"
        const val GROUP_MESSAGE_DISPATCH_QUEUE = "xinxiwang.group.message.dispatch"
        const val GROUP_MESSAGE_DISPATCH_DLQ = "xinxiwang.group.message.dispatch.dlq"
        const val GROUP_MESSAGE_DISPATCH_BUCKET_COUNT = 8
        const val GROUP_MESSAGE_DISPATCH_QUEUE_0 = "xinxiwang.group.message.dispatch.0"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_1 = "xinxiwang.group.message.dispatch.1"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_2 = "xinxiwang.group.message.dispatch.2"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_3 = "xinxiwang.group.message.dispatch.3"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_4 = "xinxiwang.group.message.dispatch.4"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_5 = "xinxiwang.group.message.dispatch.5"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_6 = "xinxiwang.group.message.dispatch.6"
        const val GROUP_MESSAGE_DISPATCH_QUEUE_7 = "xinxiwang.group.message.dispatch.7"
        const val GROUP_MESSAGE_DISPATCH_DLQ_0 = "xinxiwang.group.message.dispatch.0.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_1 = "xinxiwang.group.message.dispatch.1.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_2 = "xinxiwang.group.message.dispatch.2.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_3 = "xinxiwang.group.message.dispatch.3.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_4 = "xinxiwang.group.message.dispatch.4.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_5 = "xinxiwang.group.message.dispatch.5.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_6 = "xinxiwang.group.message.dispatch.6.dlq"
        const val GROUP_MESSAGE_DISPATCH_DLQ_7 = "xinxiwang.group.message.dispatch.7.dlq"

        val GROUP_MESSAGE_DISPATCH_QUEUES = listOf(
            GROUP_MESSAGE_DISPATCH_QUEUE_0,
            GROUP_MESSAGE_DISPATCH_QUEUE_1,
            GROUP_MESSAGE_DISPATCH_QUEUE_2,
            GROUP_MESSAGE_DISPATCH_QUEUE_3,
            GROUP_MESSAGE_DISPATCH_QUEUE_4,
            GROUP_MESSAGE_DISPATCH_QUEUE_5,
            GROUP_MESSAGE_DISPATCH_QUEUE_6,
            GROUP_MESSAGE_DISPATCH_QUEUE_7
        )

        val GROUP_MESSAGE_DISPATCH_DLQS = listOf(
            GROUP_MESSAGE_DISPATCH_DLQ_0,
            GROUP_MESSAGE_DISPATCH_DLQ_1,
            GROUP_MESSAGE_DISPATCH_DLQ_2,
            GROUP_MESSAGE_DISPATCH_DLQ_3,
            GROUP_MESSAGE_DISPATCH_DLQ_4,
            GROUP_MESSAGE_DISPATCH_DLQ_5,
            GROUP_MESSAGE_DISPATCH_DLQ_6,
            GROUP_MESSAGE_DISPATCH_DLQ_7
        )

        fun groupMessageDispatchBucket(conversationId: String): Int =
            (conversationId.hashCode() and Int.MAX_VALUE) % GROUP_MESSAGE_DISPATCH_BUCKET_COUNT

        fun groupMessageDispatchRoutingKey(bucket: Int): String = "bucket.$bucket"

        fun groupMessageDispatchRoutingKeyForConversation(conversationId: String): String =
            groupMessageDispatchRoutingKey(groupMessageDispatchBucket(conversationId))
    }

    // ── JSON message converter ──

    @Bean
    fun mqMessageConverter(jsonMapper: JsonMapper): MessageConverter =
        JacksonJsonMessageConverter(jsonMapper)

    @Bean
    fun rabbitTemplate(
        configurer: RabbitTemplateConfigurer,
        connectionFactory: ConnectionFactory,
    ): RabbitTemplate {
        val template = NamespacedRabbitTemplate(rabbitNames)
        configurer.configure(template, connectionFactory)
        return template
    }

    @Bean
    fun rabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        mqMessageConverter: MessageConverter
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(mqMessageConverter)
        factory.setConcurrentConsumers(4)
        factory.setMaxConcurrentConsumers(16)
        factory.setPrefetchCount(25)
        val retryTemplate = RetryTemplate()
        retryTemplate.setRetryPolicy(SimpleRetryPolicy(3))
        val backOffPolicy = ExponentialBackOffPolicy()
        backOffPolicy.initialInterval = 1000L
        backOffPolicy.multiplier = 2.0
        backOffPolicy.maxInterval = 10000L
        retryTemplate.setBackOffPolicy(backOffPolicy)

        val interceptor = RetryOperationsInterceptor()
        interceptor.setRetryOperations(retryTemplate)
        interceptor.setRecoverer { _, cause ->
            throw AmqpRejectAndDontRequeueException(cause)
        }
        factory.setAdviceChain(interceptor)
        return factory
    }

    @Bean
    fun highThroughputContainerFactory(
        connectionFactory: ConnectionFactory,
        mqMessageConverter: MessageConverter
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(mqMessageConverter)
        factory.setConcurrentConsumers(8)
        factory.setMaxConcurrentConsumers(32)
        factory.setPrefetchCount(50)
        val retryTemplate = RetryTemplate()
        retryTemplate.setRetryPolicy(SimpleRetryPolicy(3))
        val backOffPolicy = ExponentialBackOffPolicy()
        backOffPolicy.initialInterval = 1000L
        backOffPolicy.multiplier = 2.0
        backOffPolicy.maxInterval = 10000L
        retryTemplate.setBackOffPolicy(backOffPolicy)
        val interceptor = RetryOperationsInterceptor()
        interceptor.setRetryOperations(retryTemplate)
        interceptor.setRecoverer { _, cause ->
            throw AmqpRejectAndDontRequeueException(cause)
        }
        factory.setAdviceChain(interceptor)
        return factory
    }

    // ── Topic Exchange: cross-node routing ──

    @Bean
    fun routeExchange(): TopicExchange = TopicExchange(rabbitNames.routeExchange, true, false)

    @Bean
    fun nodeQueue(): Queue = QueueBuilder
        .nonDurable(rabbitNames.nodeQueueName)
        .autoDelete()
        .ttl(60_000)
        .build()

    @Bean
    fun nodeBinding(routeExchange: TopicExchange, nodeQueue: Queue): Binding =
        BindingBuilder.bind(nodeQueue).to(routeExchange).with("node.$nodeId")

    // ── Direct Queue: message persistence (with DLQ) ──

    @Bean
    fun messagePersistDlq(): Queue = QueueBuilder.durable(rabbitNames.messagePersistDlq).build()

    @Bean
    fun messagePersistQueue(): Queue = QueueBuilder
        .durable(rabbitNames.messagePersistQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.messagePersistDlq)
        .build()

    // ── Direct Queue: ACK timeout retry (with DLQ) ──

    @Bean fun ackTimeoutDlq(): Queue = QueueBuilder.durable(rabbitNames.ackTimeoutDlq).build()
    @Bean fun ackTimeoutQueue(): Queue = QueueBuilder
        .durable(rabbitNames.ackTimeoutQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.ackTimeoutDlq)
        .build()

    // ── Fanout Exchange: group events ──

    @Bean
    fun groupEventExchange(): FanoutExchange = FanoutExchange(rabbitNames.eventGroupExchange, true, false)

    @Bean
    fun groupEventQueue(): Queue = QueueBuilder
        .nonDurable(rabbitNames.groupEventQueueName)
        .autoDelete()
        .build()

    @Bean
    fun groupEventBinding(groupEventExchange: FanoutExchange, groupEventQueue: Queue): Binding =
        BindingBuilder.bind(groupEventQueue).to(groupEventExchange)

    // ── Fanout Exchange: friend events ──

    @Bean
    fun friendEventExchange(): FanoutExchange = FanoutExchange(rabbitNames.eventFriendExchange, true, false)

    @Bean
    fun friendEventQueue(): Queue = QueueBuilder
        .nonDurable(rabbitNames.friendEventQueueName)
        .autoDelete()
        .build()

    @Bean
    fun friendEventBinding(friendEventExchange: FanoutExchange, friendEventQueue: Queue): Binding =
        BindingBuilder.bind(friendEventQueue).to(friendEventExchange)

    // ── Fanout Exchange: conversation events ──

    @Bean
    fun conversationEventExchange(): FanoutExchange =
        FanoutExchange(rabbitNames.eventConversationExchange, true, false)

    @Bean
    fun conversationEventQueue(): Queue = QueueBuilder
        .nonDurable(rabbitNames.conversationEventQueueName)
        .autoDelete()
        .build()

    @Bean
    fun conversationEventBinding(
        conversationEventExchange: FanoutExchange,
        conversationEventQueue: Queue
    ): Binding = BindingBuilder.bind(conversationEventQueue).to(conversationEventExchange)

    // ── Async processing queues ──

    @Bean fun messageRecallDlq(): Queue = QueueBuilder.durable(rabbitNames.messageRecallDlq).build()
    @Bean fun messageRecallQueue(): Queue = QueueBuilder
        .durable(rabbitNames.messageRecallQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.messageRecallDlq)
        .build()

    @Bean fun messageDeleteDlq(): Queue = QueueBuilder.durable(rabbitNames.messageDeleteDlq).build()
    @Bean fun messageDeleteQueue(): Queue = QueueBuilder
        .durable(rabbitNames.messageDeleteQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.messageDeleteDlq)
        .build()

    @Bean fun friendAcceptDlq(): Queue = QueueBuilder.durable(rabbitNames.friendAcceptDlq).build()
    @Bean fun friendAcceptQueue(): Queue = QueueBuilder
        .durable(rabbitNames.friendAcceptQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.friendAcceptDlq)
        .build()

    @Bean fun momentLikeDlq(): Queue = QueueBuilder.durable(rabbitNames.momentLikeDlq).build()
    @Bean fun momentLikeQueue(): Queue = QueueBuilder
        .durable(rabbitNames.momentLikeQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.momentLikeDlq)
        .build()

    @Bean fun accountDeleteDlq(): Queue = QueueBuilder.durable(rabbitNames.accountDeleteDlq).build()
    @Bean fun accountDeleteQueue(): Queue = QueueBuilder
        .durable(rabbitNames.accountDeleteQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.accountDeleteDlq)
        .build()

    @Bean fun profileUpdateDlq(): Queue = QueueBuilder.durable(rabbitNames.profileUpdateDlq).build()
    @Bean fun profileUpdateQueue(): Queue = QueueBuilder
        .durable(rabbitNames.profileUpdateQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.profileUpdateDlq)
        .build()

    @Bean fun walletTransactionDlq(): Queue = QueueBuilder.durable(rabbitNames.walletTransactionDlq).build()
    @Bean fun walletTransactionQueue(): Queue = QueueBuilder
        .durable(rabbitNames.walletTransactionQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.walletTransactionDlq)
        .build()

    @Bean fun redpacketClaimDlq(): Queue = QueueBuilder.durable(rabbitNames.redpacketClaimDlq).build()
    @Bean fun redpacketClaimQueue(): Queue = QueueBuilder
        .durable(rabbitNames.redpacketClaimQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.redpacketClaimDlq)
        .build()

    @Bean fun broadcastDlq(): Queue = QueueBuilder.durable(rabbitNames.broadcastDlq).build()
    @Bean fun broadcastQueue(): Queue = QueueBuilder
        .durable(rabbitNames.broadcastQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.broadcastDlq)
        .build()

    // ── APNs async push queue (with DLQ) ──

    @Bean fun apnsPushDlq(): Queue = QueueBuilder.durable(rabbitNames.apnsPushDlq).build()
    @Bean fun apnsPushQueue(): Queue = QueueBuilder
        .durable(rabbitNames.apnsPushQueue)
        .deadLetterExchange("")
        .deadLetterRoutingKey(rabbitNames.apnsPushDlq)
        .build()

    // ── Group message dispatch queues (bucketed by conversationId) ──

    @Bean
    fun groupMessageDispatchExchange(): DirectExchange =
        DirectExchange(rabbitNames.groupMessageDispatchExchange, true, false)

    @Bean
    fun groupMessageDispatchDeclarables(groupMessageDispatchExchange: DirectExchange): Declarables {
        val declarables = mutableListOf<Declarable>()

        rabbitNames.groupMessageDispatchQueues.zip(rabbitNames.groupMessageDispatchDlqs).forEachIndexed { index, (queueName, dlqName) ->
            val dlq = QueueBuilder.durable(dlqName).build()
            val queue = QueueBuilder
                .durable(queueName)
                .deadLetterExchange("")
                .deadLetterRoutingKey(dlqName)
                .build()
            val binding = BindingBuilder.bind(queue)
                .to(groupMessageDispatchExchange)
                .with(groupMessageDispatchRoutingKey(index))

            declarables += dlq
            declarables += queue
            declarables += binding
        }

        // 保留旧单队列用于平滑迁移，避免部署时遗漏历史积压消息。
        declarables += QueueBuilder.durable(rabbitNames.groupMessageDispatchDlq).build()
        declarables += QueueBuilder
            .durable(rabbitNames.groupMessageDispatchQueue)
            .deadLetterExchange("")
            .deadLetterRoutingKey(rabbitNames.groupMessageDispatchDlq)
            .build()

        return Declarables(declarables)
    }
}
