package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class AdminUserCustomerServiceService(
    private val userRepository: UserRepository,
    private val accountRepository: CustomerServiceAccountRepository,
    private val userCacheInvalidationPort: UserCacheInvalidationPort,
    private val auditLogPort: AuditLogPort,
) {
    fun activeCustomerServiceUserIds(userIds: Collection<String>): Set<String> {
        val ids = userIds.mapNotNull { it.takeIf(String::isNotBlank) }.distinct()
        if (ids.isEmpty()) return emptySet()
        return accountRepository.findByUserIdInAndEnabledTrue(ids).mapTo(mutableSetOf()) { it.userId }
    }

    fun annotateCustomerServiceStatus(
        users: Collection<MutableMap<String, Any?>>,
        activeCustomerServiceUserIds: Set<String> = activeCustomerServiceUserIds(users.mapNotNull { userIdOf(it) }),
    ) {
        users.forEach { user ->
            user["isCustomerService"] = userIdOf(user) in activeCustomerServiceUserIds
        }
    }

    fun userMapWithCustomerServiceStatus(userId: String): MutableMap<String, Any?>? {
        val user = userRepository.findByIdAndIsDeletedFalse(userId) ?: return null
        val userMap = mutableMapOf<String, Any?>(
            "_id" to user.id,
            "id" to user.id,
            "username" to user.username,
            "displayName" to user.displayName,
            "avatarUrl" to user.avatarUrl,
            "gender" to user.gender,
            "bio" to user.bio,
            "inviteCode" to user.inviteCode,
            "myInviteCode" to user.myInviteCode,
            "invitedBy" to user.invitedBy,
            "version" to user.version,
            "updatedAt" to user.updatedAt,
            "momentsBgUrl" to user.momentsBgUrl,
            "momentsVisibility" to user.momentsVisibility,
            "bscAddress" to user.bscAddress,
            "walletBalance" to user.walletBalance,
            "frozenBalance" to user.frozenBalance,
            "isBot" to user.isBot,
            "isOperator" to user.isOperator,
            "assignedCsUserId" to user.assignedCsUserId,
            "assignedCsQrCodeId" to user.assignedCsQrCodeId,
            "isDeleted" to user.isDeleted,
            "deletedAt" to user.deletedAt,
            "deletedReason" to user.deletedReason,
        )
        annotateCustomerServiceStatus(listOf(userMap))
        return userMap
    }

    fun toggleCustomerService(
        adminId: String,
        adminUsername: String,
        userId: String,
        body: Map<String, Any>,
        ipAddress: String? = null,
    ): ResponseEntity<ApiResponse<*>> {
        val isCustomerService = body["isCustomerService"] as? Boolean
            ?: return ResponseEntity.badRequest().body(
                ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, "缺少 isCustomerService 参数"),
            )

        val user = userRepository.findByIdAndIsDeletedFalse(userId)
            ?: return ResponseEntity.status(404).body(
                ApiResponse.error<Any>(ErrorCode.NOT_FOUND, "用户不存在"),
            )

        val now = System.currentTimeMillis()
        val current = accountRepository.findByUserId(user.id.orEmpty())
        if (isCustomerService) {
            val next = current?.copy(enabled = true, updatedAt = now)
                ?: CustomerServiceAccount(
                    userId = user.id.orEmpty(),
                    displayName = null,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                )
            accountRepository.save(next)
        } else if (current != null) {
            accountRepository.save(current.copy(enabled = false, updatedAt = now))
        }

        userCacheInvalidationPort.invalidate(user.id.orEmpty())
        auditLogPort.recordAudit(
            adminId = adminId,
            adminUsername = adminUsername,
            action = if (isCustomerService) "SET_CUSTOMER_SERVICE" else "UNSET_CUSTOMER_SERVICE",
            targetType = "USER",
            targetId = user.id,
            details = if (isCustomerService) "设置为客服号" else "取消客服号",
            ipAddress = ipAddress,
        )

        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf("isCustomerService" to isCustomerService),
                if (isCustomerService) "已设置为客服号" else "已取消客服号",
            ),
        )
    }

    private fun userIdOf(user: Map<String, Any?>): String? =
        (user["_id"] ?: user["id"])?.toString()?.takeIf { it.isNotBlank() }
}
