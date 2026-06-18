package com.layababateam.xinxiwang_backend.consumer

import com.layababateam.xinxiwang_backend.service.WalletTransactionConsumerPort
import com.layababateam.xinxiwang_backend.service.WalletTransactionEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * 钱包流水消费壳。
 *
 * SDK 负责队列监听和 payload 解析；业务侧 [WalletTransactionConsumerPort] 保留流水模型和持久化逻辑。
 */
@Component
class WalletTransactionConsumer(
    private val walletTransactionConsumerPort: WalletTransactionConsumerPort,
) {
    private val log = LoggerFactory.getLogger(WalletTransactionConsumer::class.java)

    @RabbitListener(queues = ["#{@rabbitNames.walletTransactionQueue}"])
    fun onMessage(payload: Map<String, Any?>) {
        try {
            val event = parseEvent(payload) ?: return
            walletTransactionConsumerPort.persistWalletTransaction(event)
        } catch (e: Exception) {
            log.error("Failed to persist wallet transaction: {}", e.message, e)
            throw e
        }
    }

    private fun parseEvent(payload: Map<String, Any?>): WalletTransactionEvent? {
        val userId = payload["userId"] as? String ?: return null
        val type = (payload["type"] as? Number)?.toInt() ?: return null
        val amount = payload["amount"] as? String ?: return null
        return WalletTransactionEvent(
            userId = userId,
            type = type,
            amount = amount,
            counterpartyId = payload["counterpartyId"] as? String,
            counterpartyName = payload["counterpartyName"] as? String,
            txHash = payload["txHash"] as? String,
            address = payload["address"] as? String,
            redPacketId = payload["redPacketId"] as? String,
            status = (payload["status"] as? Number)?.toInt() ?: 1,
            remark = payload["remark"] as? String ?: "",
        )
    }
}
