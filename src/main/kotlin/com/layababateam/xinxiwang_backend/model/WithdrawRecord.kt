package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "withdraw_records")
@CompoundIndex(name = "userId_status", def = "{'userId': 1, 'status': 1}")
data class WithdrawRecord(
    @Id
    val id: String? = null,
    @Indexed
    val userId: String,
    val userName: String,
    val amount: String,               // 提现金额（String避免浮点精度问题）
    val toAddress: String,            // BSC BEP20 提现地址
    val chainType: String = "BSC",
    @Indexed
    val status: Int = 0,              // 0=审核中, 1=通过, 2=驳回
    val paymentStatus: Int = 0,       // 0=未支付, 1=支付中, 2=成功, 3=失败
    val txHash: String? = null,       // 链上交易哈希
    val rejectReason: String? = null, // 驳回原因
    val reviewedBy: String? = null,   // 审核人
    val reviewedAt: Long? = null,     // 审核时间
    val callbackData: String? = null, // 回调数据
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
