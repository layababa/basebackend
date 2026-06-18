package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.RedPacketClaimConsumerPort
import com.layababateam.xinxiwang_backend.service.RedPacketClaimEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 红包领取消费壳。
 *
 * SDK 负责队列监听和基础 payload 校验；业务侧 [RedPacketClaimConsumerPort] 保留红包、钱包和指标更新逻辑。
 */
@Component
class RedPacketClaimConsumer(
    private val redPacketClaimConsumerPort: RedPacketClaimConsumerPort,
) {
    private val log = LoggerFactory.getLogger(RedPacketClaimConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.redpacketClaimQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        val redPacketId = payload["redPacketId"] as? String
            ?: throw rejectPermanent("missing redPacketId in payload=$payload")
        val userId = payload["userId"] as? String
            ?: throw rejectPermanent("missing userId in payload=$payload")
        val amount = payload["amount"] as? String
            ?: throw rejectPermanent("missing amount in payload=$payload")
        runCatching { BigDecimal(amount) }.getOrNull()
            ?: throw rejectPermanent("invalid amount=$amount rp=$redPacketId user=$userId")

        redPacketClaimConsumerPort.claimRedPacket(
            RedPacketClaimEvent(
                redPacketId = redPacketId,
                userId = userId,
                amount = amount,
                userName = payload["userName"] as? String ?: "",
            )
        )
    }

    private fun rejectPermanent(reason: String): AmqpRejectAndDontRequeueException {
        log.error("Reject red packet claim message permanently -> DLQ: {}", reason)
        return AmqpRejectAndDontRequeueException(reason)
    }
}
