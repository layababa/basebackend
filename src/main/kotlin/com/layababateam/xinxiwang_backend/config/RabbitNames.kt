package com.layababateam.xinxiwang_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("rabbitNames")
class RabbitNames(
    @Value("\${app.environment:\${sentry.environment:production}}") appEnvironment: String,
    @Value("\${xinxiwang.rabbit.name-prefix:}") configuredNamePrefix: String,
    @Value("\${xinxiwang.node.id:node-default}") private val nodeId: String,
) {
    val namePrefix: String =
        InfrastructureNamespaces.effectiveRabbitNamePrefix(appEnvironment, configuredNamePrefix)

    fun name(baseName: String): String = InfrastructureNamespaces.prefixed(namePrefix, baseName)

    val routeExchange: String get() = name(RabbitMQConfig.ROUTE_EXCHANGE)
    val eventGroupExchange: String get() = name(RabbitMQConfig.EVENT_GROUP_EXCHANGE)
    val eventFriendExchange: String get() = name(RabbitMQConfig.EVENT_FRIEND_EXCHANGE)
    val eventConversationExchange: String get() = name(RabbitMQConfig.EVENT_CONVERSATION_EXCHANGE)

    val messagePersistQueue: String get() = name(RabbitMQConfig.MESSAGE_PERSIST_QUEUE)
    val messagePersistDlq: String get() = name(RabbitMQConfig.MESSAGE_PERSIST_DLQ)
    val ackTimeoutQueue: String get() = name(RabbitMQConfig.ACK_TIMEOUT_QUEUE)
    val ackTimeoutDlq: String get() = name(RabbitMQConfig.ACK_TIMEOUT_DLQ)

    val messageRecallQueue: String get() = name(RabbitMQConfig.MESSAGE_RECALL_QUEUE)
    val messageRecallDlq: String get() = name(RabbitMQConfig.MESSAGE_RECALL_DLQ)
    val messageDeleteQueue: String get() = name(RabbitMQConfig.MESSAGE_DELETE_QUEUE)
    val messageDeleteDlq: String get() = name(RabbitMQConfig.MESSAGE_DELETE_DLQ)
    val friendAcceptQueue: String get() = name(RabbitMQConfig.FRIEND_ACCEPT_QUEUE)
    val friendAcceptDlq: String get() = name(RabbitMQConfig.FRIEND_ACCEPT_DLQ)
    val momentLikeQueue: String get() = name(RabbitMQConfig.MOMENT_LIKE_QUEUE)
    val momentLikeDlq: String get() = name(RabbitMQConfig.MOMENT_LIKE_DLQ)
    val accountDeleteQueue: String get() = name(RabbitMQConfig.ACCOUNT_DELETE_QUEUE)
    val accountDeleteDlq: String get() = name(RabbitMQConfig.ACCOUNT_DELETE_DLQ)
    val profileUpdateQueue: String get() = name(RabbitMQConfig.PROFILE_UPDATE_QUEUE)
    val profileUpdateDlq: String get() = name(RabbitMQConfig.PROFILE_UPDATE_DLQ)
    val walletTransactionQueue: String get() = name(RabbitMQConfig.WALLET_TRANSACTION_QUEUE)
    val walletTransactionDlq: String get() = name(RabbitMQConfig.WALLET_TRANSACTION_DLQ)
    val redpacketClaimQueue: String get() = name(RabbitMQConfig.REDPACKET_CLAIM_QUEUE)
    val redpacketClaimDlq: String get() = name(RabbitMQConfig.REDPACKET_CLAIM_DLQ)
    val broadcastQueue: String get() = name(RabbitMQConfig.BROADCAST_QUEUE)
    val broadcastDlq: String get() = name(RabbitMQConfig.BROADCAST_DLQ)
    val apnsPushQueue: String get() = name(RabbitMQConfig.APNS_PUSH_QUEUE)
    val apnsPushDlq: String get() = name(RabbitMQConfig.APNS_PUSH_DLQ)

    val groupMessageDispatchExchange: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_EXCHANGE)
    val groupMessageDispatchQueue: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE)
    val groupMessageDispatchDlq: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_DLQ)
    val groupMessageDispatchQueue0: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_0)
    val groupMessageDispatchQueue1: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_1)
    val groupMessageDispatchQueue2: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_2)
    val groupMessageDispatchQueue3: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_3)
    val groupMessageDispatchQueue4: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_4)
    val groupMessageDispatchQueue5: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_5)
    val groupMessageDispatchQueue6: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_6)
    val groupMessageDispatchQueue7: String get() = name(RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUE_7)

    val nodeQueueName: String get() = name("xinxiwang.route.node.$nodeId")
    val groupEventQueueName: String get() = name("xinxiwang.event.group.node.$nodeId")
    val friendEventQueueName: String get() = name("xinxiwang.event.friend.node.$nodeId")
    val conversationEventQueueName: String get() = name("xinxiwang.event.conversation.node.$nodeId")

    val groupMessageDispatchQueues: List<String>
        get() = RabbitMQConfig.GROUP_MESSAGE_DISPATCH_QUEUES.map(::name)

    val groupMessageDispatchDlqs: List<String>
        get() = RabbitMQConfig.GROUP_MESSAGE_DISPATCH_DLQS.map(::name)
}
