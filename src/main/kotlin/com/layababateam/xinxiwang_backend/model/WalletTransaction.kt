package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "wallet_transactions")
@CompoundIndex(
    name = "uniq_redpacket_tx",
    def = "{'userId': 1, 'redPacketId': 1, 'type': 1}",
    unique = true,
    sparse = true
)
data class WalletTransaction(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,               // 交易所属用户
    val type: Int,                     // 0=充值 1=提现 2=转账收入 3=转账支出 4=红包发出 5=红包领取 6=红包退款
    val amount: String,               // 金额
    val counterpartyId: String? = null,  // 对方用户ID（转账/红包时）
    val counterpartyName: String? = null, // 对方用户名
    @Indexed
    val txHash: String? = null,       // 链上交易哈希
    val address: String? = null,      // 相关地址
    val redPacketId: String? = null,  // 红包ID
    val status: Int = 1,              // 0=处理中 1=成功 2=失败
    val remark: String = "",          // 备注
    val createdAt: Long = System.currentTimeMillis()
)
