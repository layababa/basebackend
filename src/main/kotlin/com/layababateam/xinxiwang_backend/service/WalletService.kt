package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.extensions.toPositiveAmount
import com.layababateam.xinxiwang_backend.extensions.toSafeAmount
import com.layababateam.xinxiwang_backend.model.*
import com.layababateam.xinxiwang_backend.repository.RedPacketRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WalletTransactionRepository
import com.layababateam.xinxiwang_backend.repository.WithdrawRecordRepository
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class WalletService(
    private val userRepository: UserRepository,
    private val walletTransactionRepository: WalletTransactionRepository,
    private val redPacketRepository: RedPacketRepository,
    private val withdrawRecordRepository: WithdrawRecordRepository,
    private val payNotificationService: PayNotificationService,
    private val redPacketCacheService: RedPacketCacheService,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val userCacheService: UserCacheService,
    private val passwordEncoder: org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder,
    private val aesPasswordEncoder: com.layababateam.xinxiwang_backend.config.AesPasswordEncoder,
    private val walletConfigService: WalletConfigService
) {
    private val log = LoggerFactory.getLogger(WalletService::class.java)

    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var distributedLockService: DistributedLockService

    @org.springframework.beans.factory.annotation.Value("\${rentmsg.wallet.api-key}")
    private lateinit var apiKey: String

    @org.springframework.beans.factory.annotation.Value("\${rentmsg.wallet.api-base-url}")
    private lateinit var apiBaseUrl: String
    private val restTemplate: RestTemplate = RestTemplate(
        org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(java.time.Duration.ofSeconds(5))
            setReadTimeout(java.time.Duration.ofSeconds(30))
        }
    )

    // ─── HMAC-SHA256 Signature ────────────────────────────────────
    private fun generateSignature(chain: String, timestamp: String): String {
        val payload = chain + timestamp
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiKey.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // ─── Create BSC Deposit Address ──────────────────────────────
    fun createDepositAddress(userId: String): String {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        
        // Return existing address if already created
        if (!user.bscAddress.isNullOrBlank()) {
            return user.bscAddress
        }

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val chain = "bsc"
        val signature = generateSignature(chain, timestamp)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Api-Key", apiKey)

        val body = mapOf(
            "chain" to chain,
            "unique_key" to "user_$userId",
            "timestamp" to timestamp,
            "signature" to signature
        )

        return try {
            val response = restTemplate.postForObject(
                "$apiBaseUrl/wallet/create",
                HttpEntity(body, headers),
                Map::class.java
            )

            @Suppress("UNCHECKED_CAST")
            val data = response?.get("data") as? Map<String, Any>
            val address = data?.get("address") as? String
                ?: throw RuntimeException("获取地址失败")

            // Save to user
            userRepository.save(user.copy(bscAddress = address))
            log.info("Created BSC address for user {}: {}", userId, address)
            address
        } catch (e: Exception) {
            log.error("Failed to create BSC address for user {}: {}", userId, e.message)
            throw RuntimeException("创建充值地址失败: ${e.message}")
        }
    }

    // ─── Get Balance ─────────────────────────────────────────────
    fun getBalance(userId: String): String {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        return user.walletBalance
    }

    // ─── Get Wallet Info (balance + frozenBalance + hasPaymentPassword + currencies) ──
    fun getWalletInfo(userId: String): Map<String, Any> {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        val enabledCurrencies = walletConfigService.getEnabledCurrencies()
        return mapOf(
            "balance" to user.walletBalance,
            "frozenBalance" to user.frozenBalance,
            "hasPaymentPassword" to (user.paymentPasswordHash != null).toString(),
            "currencies" to enabledCurrencies.map { c ->
                mapOf(
                    "currencyId" to c.currencyId,
                    "balance" to if (c.currencyId == "points") user.walletBalance else "0",
                    "frozenBalance" to if (c.currencyId == "points") user.frozenBalance else "0"
                )
            }
        )
    }

    // ─── Withdraw (Submit for Review) ────────────────────────────
    fun withdraw(userId: String, toAddress: String, amount: String, paymentPassword: String): Map<String, Any?> {
        return distributedLockService.withLock("wallet:$userId") {
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }

            // 1. 验证支付密码
            if (user.paymentPasswordHash == null) {
                throw IllegalStateException("请先设置支付密码")
            }
            if (!aesPasswordEncoder.matches(paymentPassword, user.paymentPasswordHash)) {
                throw IllegalArgumentException("支付密码错误")
            }

            // 2. 计算可用余额 = walletBalance - frozenBalance
            val balance = user.walletBalance.toSafeAmount()
            val frozen = user.frozenBalance.toSafeAmount()
            val availableBalance = balance.subtract(frozen)
            val withdrawAmount = amount.toPositiveAmount()

            // 3. 验证可用余额 >= 提现金额
            if (availableBalance < withdrawAmount) {
                throw IllegalArgumentException("可用余额不足（当前可用: ${availableBalance.toPlainString()}）")
            }

            // 4. 增加冻结余额
            val newFrozen = frozen.add(withdrawAmount).toPlainString()
            userRepository.save(user.copy(frozenBalance = newFrozen))

            // 5. 创建提现记录（status=0 审核中）
            val withdrawRecord = withdrawRecordRepository.save(WithdrawRecord(
                userId = userId,
                userName = user.displayName,
                amount = amount,
                toAddress = toAddress,
                status = 0
            ))

            // 6. 创建钱包流水（type=1 提现, status=0 处理中）
            walletTransactionRepository.save(WalletTransaction(
                userId = userId,
                type = 1, // 提现
                amount = amount,
                address = toAddress,
                txHash = "withdraw:${withdrawRecord.id}",
                status = 0, // 处理中
                remark = "提现至 $toAddress（审核中）"
            ))

            userCacheService.invalidate(userId)

            log.info("User {} submitted withdraw request {} ({} 积分 to {})", userId, withdrawRecord.id, amount, toAddress)
            mapOf(
                "withdrawId" to withdrawRecord.id,
                "status" to "pending",
                "message" to "提现申请已提交，等待审核",
                "availableBalance" to availableBalance.subtract(withdrawAmount).toPlainString()
            )
        }
    }

    // ─── Handle Deposit Callback ────────────────────────────────
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var redisTemplate: org.springframework.data.redis.core.StringRedisTemplate

    fun handleDepositCallback(toAddress: String, amount: String, txHash: String, signature: String, timestamp: String) {
        // 1. Verify callback signature
        val expectedSig = generateSignature(toAddress + amount + txHash, timestamp)
        if (signature != expectedSig) {
            log.warn("Invalid deposit callback signature for txHash: {}", txHash)
            throw SecurityException("Invalid callback signature")
        }

        // 2. Prevent replay: check txHash deduplication
        val dedupKey = "rentmsg:deposit:tx:$txHash"
        if (redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", java.time.Duration.ofDays(7)) != true) {
            log.warn("Duplicate deposit callback for txHash: {}", txHash)
            return
        }

        // 3. Find user by BSC address
        val user = userRepository.findByBscAddress(toAddress) ?: run {
            log.warn("Deposit callback for unknown address: {}", toAddress)
            return
        }

        val userId = user.id ?: return

        // 4. 使用分散式鎖保護餘額更新
        distributedLockService.withLock("wallet:$userId") {
            // 重新讀取最新餘額（鎖內讀取避免髒讀）
            val freshUser = userRepository.findById(userId).orElse(null) ?: return@withLock
            val balance = freshUser.walletBalance.toSafeAmount()
            val depositAmount = amount.toSafeAmount()
            val newBalance = balance.add(depositAmount).toPlainString()

            userRepository.save(freshUser.copy(walletBalance = newBalance))

            walletTransactionRepository.save(WalletTransaction(
                userId = userId,
                type = 0, // 充值
                amount = amount,
                txHash = txHash,
                address = toAddress,
                status = 1,
                remark = "BSC链充值"
            ))

            log.info("Deposit {} 积分 for user {} (tx: {})", amount, userId, txHash)
        }
    }

    // ─── Internal Transfer (Off-chain) ──────────────────────────
    fun internalTransfer(senderId: String, receiverId: String, amount: String, remark: String = ""): Map<String, String> {
        // 按字典序排序鎖鍵，避免死鎖（A→B 和 B→A 同時發生時）
        val keys = listOf("wallet:$senderId", "wallet:$receiverId").sorted()
        return distributedLockService.withLock(keys[0]) {
            distributedLockService.withLock(keys[1]) {
            val sender = userRepository.findById(senderId).orElseThrow { IllegalArgumentException("发送方不存在") }
            val receiver = userRepository.findById(receiverId).orElseThrow { IllegalArgumentException("接收方不存在") }

            val transferAmount = amount.toPositiveAmount()
            val senderBalance = sender.walletBalance.toSafeAmount()

            if (senderBalance < transferAmount) throw IllegalArgumentException("余额不足")

            val newSenderBalance = senderBalance.subtract(transferAmount).toPlainString()
            val newReceiverBalance = receiver.walletBalance.toSafeAmount().add(transferAmount).toPlainString()

            userRepository.save(sender.copy(walletBalance = newSenderBalance))
            userRepository.save(receiver.copy(walletBalance = newReceiverBalance))
            userCacheService.invalidate(senderId)
            userCacheService.invalidate(receiverId)

            // Record transactions via MQ async
            val safeRemark = remark.take(50)
            val senderRemark = if (safeRemark.isNotBlank()) "转账给 ${receiver.displayName}（$safeRemark）" else "转账给 ${receiver.displayName}"
            val receiverRemark = if (safeRemark.isNotBlank()) "来自 ${sender.displayName} 的转账（$safeRemark）" else "来自 ${sender.displayName} 的转账"
            rabbitPublishService.send(
                RabbitMQConfig.WALLET_TRANSACTION_QUEUE,
                mapOf(
                    "userId" to senderId,
                    "type" to 3,
                    "amount" to amount,
                    "counterpartyId" to receiverId,
                    "counterpartyName" to receiver.displayName,
                    "status" to 1,
                    "remark" to senderRemark,
                ),
                "wallet_transfer_out user=$senderId counterparty=$receiverId",
            )
            rabbitPublishService.send(
                RabbitMQConfig.WALLET_TRANSACTION_QUEUE,
                mapOf(
                    "userId" to receiverId,
                    "type" to 2,
                    "amount" to amount,
                    "counterpartyId" to senderId,
                    "counterpartyName" to sender.displayName,
                    "status" to 1,
                    "remark" to receiverRemark,
                ),
                "wallet_transfer_in user=$receiverId counterparty=$senderId",
            )

            log.info("Internal transfer: {} -> {} ({} 积分)", senderId, receiverId, amount)
            mapOf(
                "senderBalance" to newSenderBalance,
                "receiverBalance" to newReceiverBalance,
                "senderName" to sender.displayName,
                "receiverName" to receiver.displayName
            )
            }
        }
    }

    // ─── Send Red Packet ────────────────────────────────────────
    fun sendRedPacket(
        senderId: String,
        conversationId: String,
        totalAmount: String,
        count: Int,
        type: Int, // 0=普通 1=拼手气 2=平均 3=指定
        greeting: String = "恭喜发财，大吉大利",
        targetUserId: String? = null
    ): RedPacket {
        return distributedLockService.withLock("wallet:$senderId") {
            val sender = userRepository.findById(senderId).orElseThrow { IllegalArgumentException("用户不存在") }
            val amount = totalAmount.toPositiveAmount()
            val balance = sender.walletBalance.toSafeAmount()

            if (count <= 0) throw IllegalArgumentException("红包个数必须大于0")
            if (balance < amount) throw IllegalArgumentException("余额不足")
            if (type == RedPacketType.EXCLUSIVE.value && targetUserId.isNullOrBlank()) throw IllegalArgumentException("指定红包需要指定目标用户")

            // Deduct sender balance
            val newBalance = balance.subtract(amount).toPlainString()
            userRepository.save(sender.copy(walletBalance = newBalance))

            // Create red packet
            val redPacket = redPacketRepository.save(RedPacket(
                senderId = senderId,
                senderName = sender.displayName,
                conversationId = conversationId,
                type = type,
                totalAmount = totalAmount,
                count = count,
                remainingAmount = totalAmount,
                remainingCount = count,
                greeting = greeting,
                targetUserId = targetUserId
            ))

            // Initialize Redis Hash for atomic claiming
            redPacketCacheService.initRedPacket(redPacket)
            userCacheService.invalidate(senderId)

            // Record transaction via MQ async
            rabbitPublishService.send(
                RabbitMQConfig.WALLET_TRANSACTION_QUEUE,
                mapOf(
                    "userId" to senderId,
                    "type" to 4,
                    "amount" to totalAmount,
                    "redPacketId" to redPacket.id,
                    "status" to 1,
                    "remark" to "发出红包",
                ),
                "wallet_red_packet_send user=$senderId redPacketId=${redPacket.id}",
            )

            log.info("User {} sent red packet {} ({} 积分 x {})", senderId, redPacket.id, totalAmount, count)
            redPacket
        }
    }

    // ── Claim Red Packet (Atomic via Redis Lua) ─────────────────
    fun claimRedPacket(userId: String, redPacketId: String): Map<String, Any?> {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }

        // Pre-validation: check type restrictions from MongoDB
        val redPacket = redPacketRepository.findById(redPacketId).orElseThrow { IllegalArgumentException("红包不存在") }
        if (redPacket.type == RedPacketType.NORMAL.value && userId == redPacket.senderId) {
            throw IllegalStateException("不能领取自己发的红包")
        }
        if (redPacket.type == RedPacketType.EXCLUSIVE.value && userId != redPacket.targetUserId) {
            throw IllegalStateException("该红包仅指定用户可领取")
        }

        // Atomic claim via Redis Lua script
        val result = redPacketCacheService.atomicClaim(userId, user.displayName, redPacketId)
        if (!result.success) {
            throw IllegalStateException(result.errorMsg ?: "领取失败")
        }

        val claimAmountStr = result.amount!!

        // Update user balance synchronously (critical for consistency)
        val newBalance = user.walletBalance.toSafeAmount().add(claimAmountStr.toSafeAmount()).toPlainString()
        try {
            userRepository.save(user.copy(walletBalance = newBalance))
        } catch (e: Exception) {
            // Compensation: rollback Redis claim on MongoDB failure
            log.error("Failed to save balance for user {} after red packet claim, compensating Redis: {}", userId, e.message)
            redPacketCacheService.compensateClaim(userId, redPacketId, claimAmountStr)
            throw RuntimeException("领取红包失败，请重试")
        }
        userCacheService.invalidate(userId)

        // Async: persist claim to MongoDB via MQ
        rabbitPublishService.send(
            RabbitMQConfig.REDPACKET_CLAIM_QUEUE,
            mapOf(
                "redPacketId" to redPacketId,
                "userId" to userId,
                "userName" to user.displayName,
                "amount" to claimAmountStr,
            ),
            "red_packet_claim user=$userId redPacketId=$redPacketId",
        )

        log.info("User {} claimed {} 积分 from red packet {}", userId, claimAmountStr, redPacketId)
        return mapOf(
            "amount" to claimAmountStr,
            "newBalance" to newBalance,
            "senderId" to redPacket.senderId,
            "senderName" to redPacket.senderName,
            "claimerName" to user.displayName,
            "greeting" to redPacket.greeting,
            "remainingCount" to (result.remainingCount ?: 0),
            "remainingAmount" to (result.remainingAmount ?: "0")
        )
    }

    // ─── Transaction History ────────────────────────────────────
    fun getTransactions(userId: String, page: Int, limit: Int, type: String): Page<WalletTransaction> {
        val pageable = PageRequest.of(page, limit)
        return when (type) {
            "income" -> walletTransactionRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                userId, listOf(0, 2, 5, 6), pageable // 充值, 转账收入, 红包领取, 红包退款
            )
            "expense" -> walletTransactionRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(
                userId, listOf(1, 3, 4), pageable // 提现, 转账支出, 红包发出
            )
            else -> walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        }
    }

    // ─── Payment Password ───────────────────────────────────────

    fun setPaymentPassword(userId: String, password: String) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        val encrypted = aesPasswordEncoder.encrypt(password)
        userRepository.save(user.copy(paymentPasswordHash = encrypted))
    }

    fun verifyPaymentPassword(userId: String, password: String): Boolean {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        if (user.paymentPasswordHash == null) throw IllegalStateException("请先设置支付密码")
        return aesPasswordEncoder.matches(password, user.paymentPasswordHash!!)
    }

    fun hasPaymentPassword(userId: String): Boolean {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("用户不存在") }
        return user.paymentPasswordHash != null
    }

    // ─── Get Red Packet Info ────────────────────────────────────
    // Redis 是领取的真理；只要 key 还在 TTL 内，就用它的实时态覆盖 MongoDB 异步落库的字段，
    // 避免 bubble 显示"还剩 N"但点击却"已领完"的展示/决策不一致。
    fun getRedPacketInfo(redPacketId: String): RedPacket {
        val mongo = redPacketRepository.findById(redPacketId)
            .orElseThrow { IllegalArgumentException("红包不存在") }
        val live = redPacketCacheService.getLiveState(redPacketId) ?: return mongo
        return mongo.copy(
            remainingCount = live.remainingCount,
            remainingAmount = live.remainingAmount
        )
    }

    // ─── Red Packet Expiry Refund ────────────────────────────────
    @Scheduled(fixedRate = 60_000) // 每分钟扫描一次
    fun refundExpiredRedPackets() {
        // Distributed lock prevents duplicate refunds across multiple instances
        val handle = distributedLockService.tryLock("redpacket:refund-scheduler", java.time.Duration.ofSeconds(50))
            ?: return

        try {
            val now = System.currentTimeMillis()
            // 每次最多處理 100 筆，避免積壓時一次性載入大量資料到記憶體
            val expiredPackets = redPacketRepository
                .findByExpiredAtLessThanAndRefundedFalseAndRemainingCountGreaterThan(now, 0)
                .take(100)

            if (expiredPackets.isEmpty()) return

            log.info("Found {} expired red packets to refund", expiredPackets.size)
            for (packet in expiredPackets) {
                try {
                    refundRedPacket(packet)
                } catch (e: Exception) {
                    log.error("Failed to refund red packet {}: {}", packet.id, e.message, e)
                }
            }
        } catch (e: Exception) {
            log.error("Error scanning expired red packets: {}", e.message, e)
        } finally {
            distributedLockService.unlock(handle)
        }
    }

    internal fun refundRedPacket(packet: RedPacket) {
        distributedLockService.withLock("wallet:${packet.senderId}") {
            val refundAmount = packet.remainingAmount.toSafeAmount()
            if (refundAmount <= BigDecimal.ZERO) {
                // Nothing to refund, just mark as refunded
                redPacketRepository.save(packet.copy(refunded = true))
                return@withLock
            }

            // Refund to sender
            val sender = userRepository.findById(packet.senderId).orElse(null)
            if (sender == null) {
                log.warn("Sender {} not found for red packet {}, marking as refunded", packet.senderId, packet.id)
                redPacketRepository.save(packet.copy(refunded = true))
                return@withLock
            }

            val newBalance = sender.walletBalance.toSafeAmount().add(refundAmount).toPlainString()
            userRepository.save(sender.copy(walletBalance = newBalance))

            // Record refund transaction
            walletTransactionRepository.save(WalletTransaction(
                userId = packet.senderId,
                type = 6, // 红包退款
                amount = refundAmount.toPlainString(),
                redPacketId = packet.id,
                status = 1,
                remark = "红包已过期，退还 ${refundAmount.toPlainString()} 积分"
            ))

            // Mark red packet as refunded
            redPacketRepository.save(packet.copy(refunded = true))

            // Send notification
            payNotificationService.sendPayNotification(
                userId = packet.senderId,
                notifType = "red_packet_refund",
                title = "红包退款通知",
                amount = refundAmount.toPlainString(),
                detail = "您发出的红包已过期，剩余 ${refundAmount.toPlainString()} 积分已退还至您的账户"
            )

            log.info("Refunded {} 积分 to user {} from expired red packet {}",
                refundAmount.toPlainString(), packet.senderId, packet.id)
        }
    }

    private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal {
        return if (this < min) min else this
    }
}
