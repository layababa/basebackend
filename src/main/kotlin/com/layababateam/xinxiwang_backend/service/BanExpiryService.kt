package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.repository.DeviceSessionRepository
import com.layababateam.xinxiwang_backend.repository.UserBanRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class BanExpiryService(
    private val userBanRepository: UserBanRepository,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val redisTemplate: StringRedisTemplate,
    private val auditLogService: AuditLogService,
    private val officialNotificationService: OfficialNotificationService,
    private val userSessionManager: UserSessionManager,
    private val distributedLockService: DistributedLockService
) {
    private val log = LoggerFactory.getLogger(BanExpiryService::class.java)

    @Scheduled(fixedRate = 60_000) // Every minute
    fun processExpiredBans() {
        val handle = distributedLockService.tryLock("ban:expiry-scheduler", Duration.ofSeconds(50))
            ?: return

        try {
            val now = System.currentTimeMillis()
            val expiredBans = userBanRepository.findByIsActiveTrueAndTypeAndExpiresAtLessThan("TEMPORARY", now)

            if (expiredBans.isEmpty()) return

            log.info("Found {} expired bans to auto-unban", expiredBans.size)
            for (ban in expiredBans) {
                try {
                    userBanRepository.save(
                        ban.copy(
                            isActive = false,
                            unbannedAt = now,
                            unbannedBy = "SYSTEM"
                        )
                    )

                    auditLogService.log(
                        adminId = "SYSTEM",
                        adminUsername = "SYSTEM",
                        action = "AUTO_UNBAN_USER",
                        targetType = "USER",
                        targetId = ban.userId,
                        details = "封禁到期自动解封"
                    )

                    // Notify user about unban
                    officialNotificationService.notifyUser(
                        userId = ban.userId,
                        content = "您的帐号封禁已到期，已自动解封。请遵守平台规范，避免再次违规。"
                    )

                    log.info("Auto-unbanned user {}, banId={}", ban.userId, ban.id)
                } catch (e: Exception) {
                    log.error("Failed to auto-unban user {}: {}", ban.userId, e.message, e)
                }
            }
        } catch (e: Exception) {
            log.error("Error processing expired bans: {}", e.message, e)
        } finally {
            distributedLockService.unlock(handle)
        }
    }

    /**
     * Invalidate all sessions and APNs tokens for a banned user.
     */
    fun invalidateUserSessions(userId: String) {
        try {
            val sessions = deviceSessionRepository.findByUserId(userId)
            // 只清 Redis token，不做多餘的 save（後面會直接刪除所有 session）
            sessions.forEach { session ->
                redisTemplate.delete("rentmsg:tokens:${session.token}")
            }
            // 批次刪除所有 device sessions
            deviceSessionRepository.deleteByUserId(userId)
            // 斷開 WebSocket
            userSessionManager.disconnectUser(userId)
            log.info("Invalidated all sessions for banned user {}, {} sessions removed", userId, sessions.size)
        } catch (e: Exception) {
            log.error("Failed to invalidate sessions for user {}: {}", userId, e.message, e)
        }
    }
}
