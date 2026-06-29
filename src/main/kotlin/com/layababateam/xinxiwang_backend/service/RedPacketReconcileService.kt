package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ClaimRecord
import com.layababateam.xinxiwang_backend.repository.RedPacketRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * 单个红包对账结果。每一项是诊断快照 + 是否落库。
 *
 * - skipped=true 表示无需修改（数据已对齐 / Redis 不存在 / Mongo 不存在）
 * - applied=true 表示 dryRun=false 且实际写回了 MongoDB
 * - missingInMongo: Redis claimed 集合里有，MongoDB.claimedBy 没有 —— 消费者漏的
 * - extraInMongo:   MongoDB.claimedBy 里有，Redis claimed 集合没有 —— 历史补录或人工干预的痕迹，不会自动改
 */
/**
 * 红包"展示态 vs 决策态"对账。
 *
 * 背景：claimRedPacket 走 Redis Lua 原子扣减是真理，MongoDB 的 claimedBy/remainingCount 由 MQ
 * 异步落库；只要 RedPacketClaimConsumer 消费失败/卡住，MongoDB 就会落后于 Redis，导致：
 *   - bubble 显示"还剩 N"但点击"已领完"（已通过 getRedPacketInfo 用 Redis 覆盖暂时缓解）
 *   - refundExpiredRedPackets 误退款给发送者（Mongo remainingCount > 0 但实际 0）
 *
 * 本服务以 Redis 为基线：
 *   1. remainingCount / remainingAmount 用 Redis 值覆盖 MongoDB
 *   2. Redis claimed 集合中存在但 MongoDB.claimedBy 缺失的用户 → 补占位 ClaimRecord
 *      （金额已无法追溯，标记为 0.00 + userName 加 "[对账补录]" 后缀）
 *
 * 默认 dryRun，避免误改。Redis key 已过期（>25h）的红包跳过 —— 没有真理可对。
 */
@Service
class RedPacketReconcileService(
    private val redPacketRepository: RedPacketRepository,
    private val redisTemplate: StringRedisTemplate,
    private val userRepository: UserRepository,
) : AdminRedPacketReconcilePort {
    private val log = LoggerFactory.getLogger(RedPacketReconcileService::class.java)

    private companion object {
        const val KEY_PREFIX = "rentmsg:redpacket:"
        const val MAX_SCAN_LIMIT = 500
        fun rpKey(id: String) = "$KEY_PREFIX{rp:$id}"
        fun claimedKey(id: String) = "$KEY_PREFIX{rp:$id}:claimed"
    }

    override fun reconcile(redPacketId: String, dryRun: Boolean): RedPacketReconcileReport {
        val mongo = redPacketRepository.findById(redPacketId).orElse(null)
            ?: return RedPacketReconcileReport(
                redPacketId = redPacketId,
                skipped = true,
                skipReason = "MongoDB 不存在该红包"
            )

        val key = rpKey(redPacketId)
        val clmKey = claimedKey(redPacketId)
        val hashOps = redisTemplate.opsForHash<String, String>()
        val redisExists = redisTemplate.hasKey(key) ?: false
        if (!redisExists) {
            return RedPacketReconcileReport(
                redPacketId = redPacketId,
                skipped = true,
                skipReason = "Redis key 已过期或不存在（25h TTL 已过），无法以 Redis 为基线对账",
                mongoRemainingCount = mongo.remainingCount,
                mongoRemainingAmount = mongo.remainingAmount,
                mongoClaimedSize = mongo.claimedBy.size
            )
        }

        val redisRemainingCount = (hashOps.get(key, "remaining_count")
            ?: hashOps.get(key, "remainingCount"))?.toIntOrNull()
            ?: return RedPacketReconcileReport(
                redPacketId = redPacketId,
                skipped = true,
                skipReason = "Redis remaining_count 字段缺失或非数字",
                mongoRemainingCount = mongo.remainingCount,
                mongoClaimedSize = mongo.claimedBy.size
            )
        val redisRemainingAmount = hashOps.get(key, "remaining_amount")
            ?: hashOps.get(key, "remainingAmount")
            ?: "0"
        val redisClaimedUsers: Set<String> =
            redisTemplate.opsForSet().members(clmKey)?.toSet() ?: emptySet()

        val mongoUsers = mongo.claimedBy.mapTo(HashSet()) { it.userId }
        val missingInMongo = (redisClaimedUsers - mongoUsers).toList()
        val extraInMongo = (mongoUsers - redisClaimedUsers).toList()

        val needSyncCount = mongo.remainingCount != redisRemainingCount
        val needSyncAmount = mongo.remainingAmount != redisRemainingAmount
        val needPlaceholders = missingInMongo.isNotEmpty()
        if (!needSyncCount && !needSyncAmount && !needPlaceholders) {
            return RedPacketReconcileReport(
                redPacketId = redPacketId,
                skipped = true,
                skipReason = "已对齐，无需修改",
                mongoRemainingCount = mongo.remainingCount,
                redisRemainingCount = redisRemainingCount,
                mongoRemainingAmount = mongo.remainingAmount,
                redisRemainingAmount = redisRemainingAmount,
                mongoClaimedSize = mongo.claimedBy.size,
                redisClaimedSize = redisClaimedUsers.size,
                extraInMongo = extraInMongo
            )
        }

        if (dryRun) {
            return RedPacketReconcileReport(
                redPacketId = redPacketId,
                applied = false,
                mongoRemainingCount = mongo.remainingCount,
                redisRemainingCount = redisRemainingCount,
                mongoRemainingAmount = mongo.remainingAmount,
                redisRemainingAmount = redisRemainingAmount,
                mongoClaimedSize = mongo.claimedBy.size,
                redisClaimedSize = redisClaimedUsers.size,
                missingInMongo = missingInMongo,
                extraInMongo = extraInMongo,
                placeholdersAdded = missingInMongo.size
            )
        }

        val placeholders = missingInMongo.map { uid ->
            val name = runCatching {
                userRepository.findById(uid).orElse(null)?.displayName ?: uid
            }.getOrDefault(uid)
            ClaimRecord(
                userId = uid,
                userName = "$name [对账补录]",
                amount = "0.00",
                claimedAt = System.currentTimeMillis()
            )
        }
        val updated = mongo.copy(
            remainingCount = redisRemainingCount,
            remainingAmount = redisRemainingAmount,
            claimedBy = mongo.claimedBy + placeholders
        )
        redPacketRepository.save(updated)
        log.warn(
            "RECONCILED rp={} count {}->{} amount {}->{} placeholders+{} extras={}",
            redPacketId,
            mongo.remainingCount, redisRemainingCount,
            mongo.remainingAmount, redisRemainingAmount,
            placeholders.size, extraInMongo
        )
        return RedPacketReconcileReport(
            redPacketId = redPacketId,
            applied = true,
            mongoRemainingCount = mongo.remainingCount,
            redisRemainingCount = redisRemainingCount,
            mongoRemainingAmount = mongo.remainingAmount,
            redisRemainingAmount = redisRemainingAmount,
            mongoClaimedSize = mongo.claimedBy.size,
            redisClaimedSize = redisClaimedUsers.size,
            missingInMongo = missingInMongo,
            extraInMongo = extraInMongo,
            placeholdersAdded = placeholders.size
        )
    }

    /**
     * 扫描最近 limit 条 remainingCount > 0 且未退款的红包，逐个对账。
     * limit 自动夹到 [1, 500]，避免一次拉太多。
     */
    override fun scan(limit: Int, dryRun: Boolean): List<RedPacketReconcileReport> {
        val capped = limit.coerceIn(1, MAX_SCAN_LIMIT)
        val candidates = redPacketRepository
            .findByRefundedFalseAndRemainingCountGreaterThanOrderByCreatedAtDesc(0, PageRequest.of(0, capped))
            .content
        return candidates.mapNotNull { rp ->
            val id = rp.id ?: return@mapNotNull null
            runCatching { reconcile(id, dryRun) }
                .onFailure { log.error("scan reconcile failed for {}: {}", id, it.message, it) }
                .getOrNull()
        }
    }
}
