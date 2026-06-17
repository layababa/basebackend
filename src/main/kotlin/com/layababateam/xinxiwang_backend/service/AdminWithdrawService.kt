package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.extensions.toSafeAmount
import com.layababateam.xinxiwang_backend.model.WalletTransaction
import com.layababateam.xinxiwang_backend.model.WithdrawRecord
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WalletTransactionRepository
import com.layababateam.xinxiwang_backend.repository.WithdrawRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class AdminWithdrawService(
    private val withdrawRecordRepository: WithdrawRecordRepository,
    private val userRepository: UserRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
    private val distributedLockPort: DistributedLockPort,
    private val auditLogPort: AuditLogPort,
    private val payNotificationPort: PayNotificationPort,
    private val userCacheInvalidationPort: UserCacheInvalidationPort
) {
    private val log = LoggerFactory.getLogger(AdminWithdrawService::class.java)

    // ─── 提现记录列表 ──────────────────────────────────────────
    fun listRecords(
        page: Int,
        size: Int,
        status: Int?,
        userId: String?,
        keyword: String?
    ): Page<WithdrawRecord> {
        val pageable = PageRequest.of(page, size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))

        // 按条件查询
        if (!userId.isNullOrBlank() && status != null) {
            return withdrawRecordRepository.findByUserIdAndStatus(userId, status, pageable)
        }
        if (!userId.isNullOrBlank()) {
            return withdrawRecordRepository.findByUserId(userId, pageable)
        }
        if (status != null) {
            return withdrawRecordRepository.findByStatus(status, pageable)
        }
        return withdrawRecordRepository.findAllByOrderByCreatedAtDesc(pageable)
    }

    // ─── 提现详情 ──────────────────────────────────────────────
    fun getRecord(recordId: String): WithdrawRecord {
        return withdrawRecordRepository.findById(recordId)
            .orElseThrow { IllegalArgumentException("提现记录不存在") }
    }

    // ─── 审批通过 ──────────────────────────────────────────────
    fun approve(recordId: String, adminId: String, adminUsername: String) {
        val record = withdrawRecordRepository.findById(recordId)
            .orElseThrow { IllegalArgumentException("提现记录不存在") }

        if (record.status != 0) {
            throw IllegalStateException("该提现记录已处理，当前状态: ${statusText(record.status)}")
        }

        val userId = record.userId

        distributedLockPort.withLock("wallet:$userId") {
            val user = userRepository.findById(userId)
                .orElseThrow { IllegalArgumentException("用户不存在") }

            val withdrawAmount = record.amount.toSafeAmount()
            val frozen = user.frozenBalance.toSafeAmount()
            val balance = user.walletBalance.toSafeAmount()

            // 1. 更新提现记录状态为通过
            val now = System.currentTimeMillis()
            val updatedRecord = record.copy(
                status = 1,
                reviewedBy = adminUsername,
                reviewedAt = now,
                updatedAt = now
            )
            withdrawRecordRepository.save(updatedRecord)

            // 2. 扣减冻结余额
            val newFrozen = frozen.subtract(withdrawAmount).coerceAtLeast(java.math.BigDecimal.ZERO).toPlainString()

            // 3. 扣减钱包余额（真正扣钱）
            val newBalance = balance.subtract(withdrawAmount).toPlainString()

            userRepository.save(user.copy(
                walletBalance = newBalance,
                frozenBalance = newFrozen
            ))
            userCacheInvalidationPort.invalidate(userId)

            // 4. 更新对应 WalletTransaction 状态
            val tx = walletTransactionRepository.findByTxHash("withdraw:${record.id}")
            if (tx != null) {
                walletTransactionRepository.save(tx.copy(
                    status = 1, // 成功
                    remark = "提现至 ${record.toAddress}（已通过审核）"
                ))
            }

            // 5. （预留）调用外部支付 API
            // TODO: 调用链上转账 API，将资金转至 record.toAddress

            // 6. 记录审计日志
            auditLogPort.recordAudit(
                adminId = adminId,
                adminUsername = adminUsername,
                action = "APPROVE_WITHDRAW",
                targetType = "WITHDRAW",
                targetId = recordId,
                details = "通过提现申请，金额: ${record.amount}，地址: ${record.toAddress}，用户: ${record.userName}",
                ipAddress = null
            )

            // 7. 通知用户
            payNotificationPort.sendPayNotificationCard(
                userId = userId,
                notifType = "withdraw_approved",
                title = "提现审核通过",
                amount = record.amount,
                detail = "您的提现申请已通过审核\n提现金额: ${record.amount} 积分\n提现地址: ${record.toAddress}",
                address = null,
                txHash = null
            )

            log.info("Admin {} approved withdraw {} for user {} (amount: {})",
                adminUsername, recordId, userId, record.amount)
        }
    }

    // ─── 审批驳回 ──────────────────────────────────────────────
    fun reject(recordId: String, adminId: String, adminUsername: String, reason: String) {
        val record = withdrawRecordRepository.findById(recordId)
            .orElseThrow { IllegalArgumentException("提现记录不存在") }

        if (record.status != 0) {
            throw IllegalStateException("该提现记录已处理，当前状态: ${statusText(record.status)}")
        }

        val userId = record.userId

        distributedLockPort.withLock("wallet:$userId") {
            val user = userRepository.findById(userId)
                .orElseThrow { IllegalArgumentException("用户不存在") }

            val withdrawAmount = record.amount.toSafeAmount()
            val frozen = user.frozenBalance.toSafeAmount()

            // 1. 更新提现记录状态为驳回
            val now = System.currentTimeMillis()
            val updatedRecord = record.copy(
                status = 2,
                rejectReason = reason,
                reviewedBy = adminUsername,
                reviewedAt = now,
                updatedAt = now
            )
            withdrawRecordRepository.save(updatedRecord)

            // 2. 退还冻结金额
            val newFrozen = frozen.subtract(withdrawAmount).coerceAtLeast(java.math.BigDecimal.ZERO).toPlainString()
            userRepository.save(user.copy(frozenBalance = newFrozen))
            userCacheInvalidationPort.invalidate(userId)

            // 3. 更新原提现流水为失败
            val tx = walletTransactionRepository.findByTxHash("withdraw:${record.id}")
            if (tx != null) {
                walletTransactionRepository.save(tx.copy(
                    status = 2, // 失败
                    remark = "提现至 ${record.toAddress}（审核驳回：$reason）"
                ))
            }

            // 4. 创建退款流水
            walletTransactionRepository.save(WalletTransaction(
                userId = userId,
                type = 7, // 提现驳回退款
                amount = record.amount,
                address = record.toAddress,
                txHash = "withdraw_refund:${record.id}",
                status = 1,
                remark = "提现审核驳回，冻结金额已退还"
            ))

            // 5. 记录审计日志
            auditLogPort.recordAudit(
                adminId = adminId,
                adminUsername = adminUsername,
                action = "REJECT_WITHDRAW",
                targetType = "WITHDRAW",
                targetId = recordId,
                details = "驳回提现申请，金额: ${record.amount}，原因: $reason，用户: ${record.userName}",
                ipAddress = null
            )

            // 6. 通知用户
            payNotificationPort.sendPayNotificationCard(
                userId = userId,
                notifType = "withdraw_rejected",
                title = "提现审核驳回",
                amount = record.amount,
                detail = "您的提现申请已被驳回\n提现金额: ${record.amount} 积分\n驳回原因: $reason\n冻结金额已退还至您的账户",
                address = null,
                txHash = null
            )

            log.info("Admin {} rejected withdraw {} for user {} (amount: {}, reason: {})",
                adminUsername, recordId, userId, record.amount, reason)
        }
    }

    private fun statusText(status: Int): String = when (status) {
        0 -> "审核中"
        1 -> "已通过"
        2 -> "已驳回"
        else -> "未知"
    }
}
