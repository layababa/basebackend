package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.layababateam.xinxiwang_backend.model.RedPacket
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

data class ClaimResult(
    val success: Boolean,
    val amount: String = "0",
    val errorMsg: String? = null,
    val remainingCount: Int = 0,
    val remainingAmount: String = "0"
)

data class RedPacketLiveState(
    val remainingCount: Int,
    val remainingAmount: String
)

@Service
class RedPacketCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(RedPacketCacheService::class.java)

    companion object {
        private const val KEY_PREFIX = "rentmsg:redpacket:"
        private val EXPIRE_TTL = Duration.ofHours(25)
        private val claimScript: DefaultRedisScript<String> = DefaultRedisScript<String>().apply {
            setLocation(ClassPathResource("scripts/redpacket_claim.lua"))
            setResultType(String::class.java)
        }

        /** 构造带 hash tag 的主 key，确保同一红包的所有 key 落在同一 slot */
        private fun redPacketKey(rpId: String): String = "${KEY_PREFIX}{rp:$rpId}"

        /** 构造带 hash tag 的 claimed key，与主 key 共享相同的 hash tag */
        private fun claimedKey(rpId: String): String = "${KEY_PREFIX}{rp:$rpId}:claimed"
    }

    fun initRedPacket(redPacket: RedPacket) {
        val rpId = redPacket.id ?: return
        val key = redPacketKey(rpId)
        val clmKey = claimedKey(rpId)

        val fields = mapOf(
            "remaining_count" to redPacket.remainingCount.toString(),
            "remaining_amount" to redPacket.remainingAmount,
            "type" to redPacket.type.toString(),
            "total_amount" to redPacket.totalAmount,
            "count" to redPacket.count.toString(),
            "sender_id" to redPacket.senderId,
            "sender_name" to redPacket.senderName,
            "greeting" to redPacket.greeting,
            "target_user_id" to (redPacket.targetUserId ?: ""),
            "expired_at" to redPacket.expiredAt.toString()
        )

        redisTemplate.opsForHash<String, String>().putAll(key, fields)
        redisTemplate.expire(key, EXPIRE_TTL)
        redisTemplate.expire(clmKey, EXPIRE_TTL)
    }

    fun atomicClaim(userId: String, userName: String, redPacketId: String): ClaimResult {
        val key = redPacketKey(redPacketId)
        val clmKey = claimedKey(redPacketId)
        val now = System.currentTimeMillis().toString()

        return try {
            val resultJson = redisTemplate.execute(
                claimScript,
                listOf(key, clmKey),
                userId, userName, now
            ) ?: return ClaimResult(success = false, errorMsg = "红包不存在")

            val map = objectMapper.readValue<Map<String, Any>>(resultJson)
            val success = map["success"] as? Boolean ?: false

            if (success) {
                ClaimResult(
                    success = true,
                    amount = map["amount"] as? String ?: "0",
                    remainingCount = (map["remainingCount"] as? Number)?.toInt() ?: 0,
                    remainingAmount = map["remainingAmount"] as? String ?: "0"
                )
            } else {
                ClaimResult(
                    success = false,
                    errorMsg = map["error"] as? String ?: "领取失败"
                )
            }
        } catch (e: Exception) {
            log.error("Red packet claim failed for user=$userId, rp=$redPacketId", e)
            ClaimResult(success = false, errorMsg = "系统错误，请稍后重试")
        }
    }

    /**
     * Compensation: rollback a claim in Redis when MongoDB save fails.
     * Re-adds the amount and increments remaining count.
     */
    fun compensateClaim(userId: String, redPacketId: String, amount: String) {
        val key = redPacketKey(redPacketId)
        val clmKey = claimedKey(redPacketId)
        try {
            val ops = redisTemplate.opsForHash<String, String>()
            // Remove user from claimed set
            redisTemplate.opsForSet().remove(clmKey, userId)
            // Restore remaining count and amount
            ops.increment(key, "remaining_count", 1)
            val currentAmount = ops.get(key, "remaining_amount") ?: "0"
            val newAmount = java.math.BigDecimal(currentAmount).add(java.math.BigDecimal(amount)).toPlainString()
            ops.put(key, "remaining_amount", newAmount)
            log.info("Compensated red packet claim: user={}, rp={}, amount={}", userId, redPacketId, amount)
        } catch (e: Exception) {
            log.error("CRITICAL: Failed to compensate red packet claim: user={}, rp={}, amount={}", userId, redPacketId, amount, e)
        }
    }

    fun getRedPacketState(redPacketId: String): Map<String, String>? {
        val key = redPacketKey(redPacketId)
        val entries = redisTemplate.opsForHash<String, String>().entries(key)
        return if (entries.isEmpty()) null else entries
    }

    /**
     * Redis 是领取的真理（Lua 原子扣减决策点）。展示路径用此方法读实时态，
     * 覆盖 MongoDB 异步落库可能滞后的 remainingCount/remainingAmount。
     * Redis key 不存在或异常 → 返回 null，调用方降级到 MongoDB。
     */
    fun getLiveState(redPacketId: String): RedPacketLiveState? = runCatching {
        val state = getRedPacketState(redPacketId) ?: return@runCatching null
        val rc = state["remaining_count"]?.toIntOrNull() ?: return@runCatching null
        val ra = state["remaining_amount"] ?: return@runCatching null
        RedPacketLiveState(remainingCount = rc, remainingAmount = ra)
    }.onFailure {
        log.warn("getLiveState failed for rp={}: {}", redPacketId, it.message)
    }.getOrNull()

    /** Redis SISMEMBER 兜底"是否已领取"，覆盖 MongoDB claimedBy 落库滞后的窗口。 */
    fun hasClaimed(redPacketId: String, userId: String): Boolean = runCatching {
        redisTemplate.opsForSet().isMember(claimedKey(redPacketId), userId) == true
    }.onFailure {
        log.warn("hasClaimed check failed for rp={} user={}: {}", redPacketId, userId, it.message)
    }.getOrDefault(false)
}
