package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.model.WalletTransaction
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WalletTransactionRepository
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * 後台錢包調整服務
 *
 * 負責管理員對使用者錢包餘額的增減操作，
 * 包含分散式鎖控制、流水記錄寫入、通知發送。
 */
@Service
class AdminWalletService(
    private val userRepository: UserRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
    private val auditLogPort: AuditLogPort,
    private val payNotificationPort: PayNotificationPort,
    private val distributedLockPort: DistributedLockPort
) {

    fun adjustBalance(
        userId: String,
        type: String,
        amount: BigDecimal,
        remark: String,
        adminId: String,
        adminUsername: String
    ): ResponseEntity<ApiResponse<*>> {
        return distributedLockPort.withLock("wallet:$userId") {
            val user = userRepository.findById(userId).orElse(null)
                ?: return@withLock ResponseEntity.status(404).body(
                    ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "用户不存在")
                )

            val balance = BigDecimal(user.walletBalance)
            val newBalance: BigDecimal
            val txType: Int
            val txHash: String
            val txAddress: String
            val txRemark: String

            if (type == "INCREASE") {
                newBalance = balance.add(amount)
                txType = 0 // 充值
                txHash = "手动上分"
                txAddress = "后台"
                txRemark = "后台手动上分${if (remark.isNotBlank()) "：$remark" else ""}"
            } else {
                if (balance < amount) {
                    return@withLock ResponseEntity.badRequest().body(
                        ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "用户余额不足，当前余额: $balance")
                    )
                }
                newBalance = balance.subtract(amount)
                txType = 1 // 提现
                txHash = "手动下分"
                txAddress = "后台"
                txRemark = "后台手动下分${if (remark.isNotBlank()) "：$remark" else ""}"
            }

            userRepository.save(user.copy(walletBalance = newBalance.toPlainString()))

            walletTransactionRepository.save(WalletTransaction(
                userId = userId,
                type = txType,
                amount = amount.toPlainString(),
                txHash = txHash,
                address = txAddress,
                status = 1,
                remark = txRemark
            ))

            auditLogPort.recordAudit(
                adminId = adminId,
                adminUsername = adminUsername,
                action = if (type == "INCREASE") "INCREASE_BALANCE" else "DECREASE_BALANCE",
                targetType = "USER",
                targetId = userId,
                details = "${if (type == "INCREASE") "增加" else "减少"}余额 ${amount.toPlainString()}，备注: $remark",
                ipAddress = null
            )

            // Notify user via pay notification service
            val actionText = if (type == "INCREASE") "充值" else "扣除"
            val remarkText = if (remark.isNotBlank()) "\n备注：$remark" else ""
            payNotificationPort.sendPayNotificationCard(
                userId = userId,
                notifType = if (type == "INCREASE") "admin_increase" else "admin_decrease",
                title = "余额变动通知",
                amount = amount.toPlainString(),
                detail = "${actionText}金额：${amount.toPlainString()} 积分\n当前余额：${newBalance.toPlainString()} 积分$remarkText",
                address = null,
                txHash = null
            )

            ResponseEntity.ok(ApiResponse.ok(
                data = mapOf(
                    "userId" to userId,
                    "previousBalance" to balance.toPlainString(),
                    "adjustAmount" to amount.toPlainString(),
                    "newBalance" to newBalance.toPlainString(),
                    "type" to type
                ),
                message = "余额调整成功"
            ))
        }
    }
}
