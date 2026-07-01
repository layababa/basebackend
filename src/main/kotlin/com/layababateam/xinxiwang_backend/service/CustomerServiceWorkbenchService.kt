package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.CustomerServiceWorkbenchProfileResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceEntryResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceMessageResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceMessagesResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceSessionResponse
import com.layababateam.xinxiwang_backend.dto.toWebCustomerServiceResponse
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.ContentType
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceContentType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceEntry
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceMessage
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSenderType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import kotlin.jvm.optionals.getOrNull

@Service
class CustomerServiceWorkbenchService(
    private val accountRepository: CustomerServiceAccountRepository,
    private val userRepository: UserRepository,
    private val entryRepository: WebCustomerServiceEntryRepository,
    private val sessionRepository: WebCustomerServiceSessionRepository,
    private val messageRepository: WebCustomerServiceMessageRepository,
    private val webCustomerServiceService: WebCustomerServiceService,
) {
    @Autowired(required = false)
    private var replyPort: CustomerServiceWorkbenchReplyPort? = null

    fun profile(customerServiceUserId: String): CustomerServiceWorkbenchProfileResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        return CustomerServiceWorkbenchProfileResponse(
            customerServiceUserId = context.user.id.orEmpty(),
            username = context.user.username,
            displayName = context.displayName,
            avatarUrl = context.user.avatarUrl,
            accountId = context.account.id.orEmpty(),
            entry = toEntryResponse(context.entry),
        )
    }

    fun listSessions(
        customerServiceUserId: String,
        status: WebCustomerServiceSessionStatus?,
        assigned: String?,
        page: Int,
        size: Int,
    ): PagedData<WebCustomerServiceSessionResponse> {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        return webCustomerServiceService.listSessions(
            entryId = context.entry.id.orEmpty(),
            status = status,
            assigned = assigned,
            adminId = customerServiceUserId,
            page = page,
            size = size,
        )
    }

    fun messages(customerServiceUserId: String, sessionId: String, before: String?, size: Int): WebCustomerServiceMessagesResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        requireOwnedSession(sessionId, context.entry.id.orEmpty())
        return webCustomerServiceService.adminMessages(sessionId, before, size)
    }

    fun claim(customerServiceUserId: String, sessionId: String): WebCustomerServiceSessionResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        requireOwnedSession(sessionId, context.entry.id.orEmpty())
        return webCustomerServiceService.claim(sessionId, customerServiceUserId, context.displayName)
    }

    fun release(customerServiceUserId: String, sessionId: String): WebCustomerServiceSessionResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        requireOwnedSession(sessionId, context.entry.id.orEmpty())
        return webCustomerServiceService.release(sessionId, customerServiceUserId, CUSTOMER_SERVICE_ROLE)
    }

    fun close(customerServiceUserId: String, sessionId: String): WebCustomerServiceSessionResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        requireOwnedSession(sessionId, context.entry.id.orEmpty())
        return webCustomerServiceService.close(sessionId, customerServiceUserId, CUSTOMER_SERVICE_ROLE)
    }

    fun sendText(customerServiceUserId: String, sessionId: String, content: String): WebCustomerServiceMessageResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        val session = requireReplyableSession(sessionId, context.entry.id.orEmpty(), customerServiceUserId)
        val body = content.trim()
        if (body.isBlank() || body.length > TEXT_MESSAGE_MAX_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "消息内容不能为空且不能超过5000字")
        }
        relayToImIfNeeded(
            session = session,
            customerServiceUserId = customerServiceUserId,
            contentType = ContentType.TEXT.value,
            content = body,
        )
        return saveMessage(
            session = session,
            senderType = WebCustomerServiceSenderType.ADMIN,
            senderId = customerServiceUserId,
            senderName = context.displayName,
            contentType = WebCustomerServiceContentType.TEXT,
            content = body,
            imageUrl = null,
        ).toWebCustomerServiceResponse()
    }

    fun sendImage(customerServiceUserId: String, sessionId: String, file: MultipartFile): WebCustomerServiceMessageResponse {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        val session = requireReplyableSession(sessionId, context.entry.id.orEmpty(), customerServiceUserId)
        val imageUrl = webCustomerServiceService.uploadCustomerServiceImage(file, requestId = null)
        relayToImIfNeeded(
            session = session,
            customerServiceUserId = customerServiceUserId,
            contentType = ContentType.IMAGE.value,
            content = imageUrl,
            imageUrl = imageUrl,
        )
        return saveMessage(
            session = session,
            senderType = WebCustomerServiceSenderType.ADMIN,
            senderId = customerServiceUserId,
            senderName = context.displayName,
            contentType = WebCustomerServiceContentType.IMAGE,
            content = IMAGE_MESSAGE_CONTENT,
            imageUrl = imageUrl,
        ).toWebCustomerServiceResponse()
    }

    fun recordExternalApiVisitorMessage(
        customerServiceUserId: String,
        externalApiCredentialId: String,
        externalAnonymousId: String,
        visitorName: String?,
        sourceUrl: String?,
        content: String,
        customerServiceQrCodeId: String? = null,
    ): WebCustomerServiceMessage {
        val context = ensureCustomerServiceContext(customerServiceUserId)
        val body = content.trim()
        if (body.isBlank() || body.length > TEXT_MESSAGE_MAX_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "message content cannot be empty or exceed 5000 characters")
        }
        val now = System.currentTimeMillis()
        val credentialId = externalApiCredentialId.trim()
        val anonymousId = externalAnonymousId.trim()
        val resolvedVisitorName = WebCustomerServiceRules.trimToNull(visitorName) ?: "Anonymous"
        val temporaryUser = ensureExternalTemporaryUser(
            credentialId = credentialId,
            anonymousId = anonymousId,
            customerServiceUserId = customerServiceUserId,
            customerServiceQrCodeId = customerServiceQrCodeId,
            visitorName = resolvedVisitorName,
        )
        val visitorUserId = temporaryUser.id.orEmpty()
        val session = sessionRepository
            .findFirstByExternalApiCredentialIdAndExternalAnonymousIdAndStatusNotOrderByLastMessageAtDesc(
                externalApiCredentialId = credentialId,
                externalAnonymousId = anonymousId,
                status = WebCustomerServiceSessionStatus.CLOSED,
            )?.let { existing ->
                existing.copy(
                    visitorId = visitorUserId,
                    status = WebCustomerServiceSessionStatus.ACTIVE,
                    assignedAdminId = customerServiceUserId,
                    assignedAdminUsername = context.displayName,
                    visitorName = resolvedVisitorName,
                    sourceUrl = WebCustomerServiceRules.trimToNull(sourceUrl) ?: existing.sourceUrl,
                    updatedAt = now,
                )
            } ?: WebCustomerServiceSession(
                entryId = context.entry.id.orEmpty(),
                visitorId = visitorUserId,
                visitorName = resolvedVisitorName,
                status = WebCustomerServiceSessionStatus.ACTIVE,
                assignedAdminId = customerServiceUserId,
                assignedAdminUsername = context.displayName,
                sourceUrl = WebCustomerServiceRules.trimToNull(sourceUrl) ?: "external-api://$credentialId",
                externalApiCredentialId = credentialId,
                externalAnonymousId = anonymousId,
                createdAt = now,
                lastMessageAt = now,
                updatedAt = now,
            )
        val savedSession = sessionRepository.save(session)
        val savedMessage = saveMessage(
            session = savedSession,
            senderType = WebCustomerServiceSenderType.VISITOR,
            senderId = visitorUserId,
            senderName = resolvedVisitorName,
            contentType = WebCustomerServiceContentType.TEXT,
            content = body,
            imageUrl = null,
        )
        relayVisitorToIm(
            session = savedSession,
            customerServiceUserId = customerServiceUserId,
            contentType = ContentType.TEXT.value,
            content = body,
        )
        return savedMessage
    }

    fun recordAssignedCustomerMessage(
        customerUserId: String,
        customerServiceUserId: String,
        contentType: Int,
        content: String,
    ): WebCustomerServiceMessage? {
        val customer = userRepository.findByIdAndIsDeletedFalse(customerUserId) ?: return null
        if (customer.isOperator || customer.assignedCsUserId != customerServiceUserId) return null
        val context = runCatching { ensureCustomerServiceContext(customerServiceUserId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val session = sessionRepository.findFirstByEntryIdAndVisitorIdAndStatusNotOrderByLastMessageAtDesc(
            entryId = context.entry.id.orEmpty(),
            visitorId = customerUserId,
            status = WebCustomerServiceSessionStatus.CLOSED,
        )?.copy(
            status = WebCustomerServiceSessionStatus.ACTIVE,
            assignedAdminId = customerServiceUserId,
            assignedAdminUsername = context.displayName,
            visitorName = customer.displayName,
            updatedAt = now,
        ) ?: WebCustomerServiceSession(
            entryId = context.entry.id.orEmpty(),
            visitorId = customerUserId,
            visitorName = customer.displayName,
            status = WebCustomerServiceSessionStatus.ACTIVE,
            assignedAdminId = customerServiceUserId,
            assignedAdminUsername = context.displayName,
            sourceUrl = "app://customer-service/$customerServiceUserId",
            createdAt = now,
            lastMessageAt = now,
            updatedAt = now,
        )
        val savedSession = sessionRepository.save(session)
        val workbenchContentType = workbenchContentType(contentType)
        return saveMessage(
            session = savedSession,
            senderType = WebCustomerServiceSenderType.VISITOR,
            senderId = customerUserId,
            senderName = customer.displayName,
            contentType = workbenchContentType,
            content = workbenchContent(contentType, content),
            imageUrl = if (workbenchContentType == WebCustomerServiceContentType.IMAGE) imageUrlFromContent(content) else null,
        )
    }

    fun recordCustomerServiceImReply(
        customerServiceUserId: String,
        customerUserId: String,
        contentType: Int,
        content: String,
    ): WebCustomerServiceMessage? {
        val customer = userRepository.findByIdAndIsDeletedFalse(customerUserId) ?: return null
        if (customer.isOperator || customer.assignedCsUserId != customerServiceUserId) return null
        val context = runCatching { ensureCustomerServiceContext(customerServiceUserId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val session = sessionRepository.findFirstByEntryIdAndVisitorIdAndStatusNotOrderByLastMessageAtDesc(
            entryId = context.entry.id.orEmpty(),
            visitorId = customerUserId,
            status = WebCustomerServiceSessionStatus.CLOSED,
        )?.copy(
            status = WebCustomerServiceSessionStatus.ACTIVE,
            assignedAdminId = customerServiceUserId,
            assignedAdminUsername = context.displayName,
            visitorName = customer.displayName,
            updatedAt = now,
        ) ?: WebCustomerServiceSession(
            entryId = context.entry.id.orEmpty(),
            visitorId = customerUserId,
            visitorName = customer.displayName,
            status = WebCustomerServiceSessionStatus.ACTIVE,
            assignedAdminId = customerServiceUserId,
            assignedAdminUsername = context.displayName,
            sourceUrl = "app://customer-service/$customerServiceUserId",
            createdAt = now,
            lastMessageAt = now,
            updatedAt = now,
        )
        val savedSession = sessionRepository.save(session)
        val workbenchContentType = workbenchContentType(contentType)
        return saveMessage(
            session = savedSession,
            senderType = WebCustomerServiceSenderType.ADMIN,
            senderId = customerServiceUserId,
            senderName = context.displayName,
            contentType = workbenchContentType,
            content = workbenchContent(contentType, content),
            imageUrl = if (workbenchContentType == WebCustomerServiceContentType.IMAGE) imageUrlFromContent(content) else null,
        )
    }

    private fun ensureCustomerServiceContext(customerServiceUserId: String): CustomerServiceContext {
        val user = userRepository.findByIdAndIsDeletedFalse(customerServiceUserId)
            ?: throw NotFoundException("客服号不存在")
        if (!user.isOperator) {
            throw ForbiddenException("当前账号不是客服号")
        }
        val account = ensureAccount(user)
        if (!account.enabled) {
            throw ForbiddenException("客服号已停用")
        }
        val entry = ensureAppEntry(user, account)
        val displayName = WebCustomerServiceRules.trimToNull(account.displayName)
            ?: WebCustomerServiceRules.trimToNull(user.displayName)
            ?: user.username
        return CustomerServiceContext(user = user, account = account, entry = entry, displayName = displayName)
    }

    private fun ensureAccount(user: User): CustomerServiceAccount {
        val userId = user.id.orEmpty()
        return accountRepository.findByUserId(userId)
            ?: accountRepository.save(
                CustomerServiceAccount(
                    userId = userId,
                    displayName = user.displayName,
                    enabled = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
    }

    private fun ensureAppEntry(user: User, account: CustomerServiceAccount): WebCustomerServiceEntry {
        val customerServiceUserId = user.id.orEmpty()
        val entryId = appEntryId(customerServiceUserId)
        val displayName = WebCustomerServiceRules.trimToNull(account.displayName)
            ?: WebCustomerServiceRules.trimToNull(user.displayName)
            ?: user.username
        val desiredName = "$displayName 接待工作台"
        val desiredAllowedDomains = listOf(APP_ENTRY_ALLOWED_DOMAIN)
        val desiredSeatAdminIds = listOf(customerServiceUserId)
        val existing = entryRepository.findById(entryId).getOrNull()
        if (existing != null) {
            val needsUpdate = existing.name != desiredName ||
                !existing.enabled ||
                existing.allowedDomains != desiredAllowedDomains ||
                existing.seatAdminIds != desiredSeatAdminIds
            if (!needsUpdate) {
                return existing
            }
            return entryRepository.save(
                existing.copy(
                    name = desiredName,
                    enabled = true,
                    allowedDomains = desiredAllowedDomains,
                    seatAdminIds = desiredSeatAdminIds,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        val now = System.currentTimeMillis()
        return entryRepository.save(
            WebCustomerServiceEntry(
                id = entryId,
                name = desiredName,
                enabled = true,
                allowedDomains = desiredAllowedDomains,
                seatAdminIds = desiredSeatAdminIds,
                welcomeMessage = "您好，请问有什么可以帮您？",
                themeColor = "#2563eb",
                createdBy = customerServiceUserId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun requireOwnedSession(sessionId: String, entryId: String): WebCustomerServiceSession {
        val session = sessionRepository.findById(sessionId).getOrNull()
            ?: throw NotFoundException("客服会话不存在")
        if (session.entryId != entryId) {
            throw ForbiddenException("不能访问其他客服号的会话")
        }
        return session
    }

    private fun requireReplyableSession(
        sessionId: String,
        entryId: String,
        customerServiceUserId: String,
    ): WebCustomerServiceSession {
        val session = requireOwnedSession(sessionId, entryId)
        if (session.status == WebCustomerServiceSessionStatus.CLOSED) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "会话已关闭")
        }
        if (session.assignedAdminId != customerServiceUserId) {
            throw ForbiddenException("请先接待该会话")
        }
        return session
    }

    private fun saveMessage(
        session: WebCustomerServiceSession,
        senderType: WebCustomerServiceSenderType,
        senderId: String?,
        senderName: String?,
        contentType: WebCustomerServiceContentType,
        content: String,
        imageUrl: String?,
    ): WebCustomerServiceMessage {
        val now = System.currentTimeMillis()
        val message = messageRepository.save(
            WebCustomerServiceMessage(
                entryId = session.entryId,
                sessionId = session.id.orEmpty(),
                senderType = senderType,
                senderId = senderId,
                senderName = senderName,
                contentType = contentType,
                content = content,
                imageUrl = imageUrl,
                createdAt = now,
            ),
        )
        sessionRepository.save(
            session.copy(
                lastMessagePreview = preview(contentType, content),
                lastMessageAt = now,
                updatedAt = now,
            ),
        )
        return message
    }

    private fun requireReplyPort(): CustomerServiceWorkbenchReplyPort =
        replyPort ?: throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "customer service reply port is not configured")

    private fun relayToImIfNeeded(
        session: WebCustomerServiceSession,
        customerServiceUserId: String,
        contentType: Int,
        content: String,
        imageUrl: String? = null,
    ) {
        val relaySession = ensureExternalSessionVisitorUser(session)
        requireReplyPort().sendCustomerServiceReply(
            CustomerServiceWorkbenchReplyCommand(
                customerServiceUserId = customerServiceUserId,
                customerUserId = relaySession.visitorId,
                contentType = contentType,
                content = content,
                imageUrl = imageUrl,
            ),
        )
    }

    private fun relayVisitorToIm(
        session: WebCustomerServiceSession,
        customerServiceUserId: String,
        contentType: Int,
        content: String,
        imageUrl: String? = null,
    ) {
        requireReplyPort().sendCustomerVisitorMessage(
            CustomerServiceWorkbenchVisitorMessageCommand(
                customerUserId = session.visitorId,
                customerServiceUserId = customerServiceUserId,
                contentType = contentType,
                content = content,
                imageUrl = imageUrl,
            ),
        )
    }

    private fun ensureExternalSessionVisitorUser(session: WebCustomerServiceSession): WebCustomerServiceSession {
        val credentialId = session.externalApiCredentialId?.trim().orEmpty()
        val anonymousId = session.externalAnonymousId?.trim().orEmpty()
        if (credentialId.isBlank() || anonymousId.isBlank()) return session
        val assignedAdminId = session.assignedAdminId?.takeIf { it.isNotBlank() } ?: return session
        val currentVisitor = userRepository.findByIdAndIsDeletedFalse(session.visitorId)
        if (currentVisitor != null && currentVisitor.assignedCsUserId == assignedAdminId) return session
        val temporaryUser = ensureExternalTemporaryUser(
            credentialId = credentialId,
            anonymousId = anonymousId,
            customerServiceUserId = assignedAdminId,
            customerServiceQrCodeId = null,
            visitorName = session.visitorName,
        )
        val visitorUserId = temporaryUser.id.orEmpty()
        if (visitorUserId.isBlank() || visitorUserId == session.visitorId) return session
        return sessionRepository.save(session.copy(visitorId = visitorUserId, updatedAt = System.currentTimeMillis()))
    }

    private fun ensureExternalTemporaryUser(
        credentialId: String,
        anonymousId: String,
        customerServiceUserId: String,
        customerServiceQrCodeId: String?,
        visitorName: String?,
    ): User {
        val username = externalTemporaryUsername(credentialId, anonymousId)
        val displayName = WebCustomerServiceRules.trimToNull(visitorName) ?: "Anonymous"
        val existing = userRepository.findByUsername(username)
        if (existing != null) {
            val updated = existing.copy(
                displayName = displayName,
                assignedCsUserId = customerServiceUserId,
                assignedCsQrCodeId = WebCustomerServiceRules.trimToNull(customerServiceQrCodeId) ?: existing.assignedCsQrCodeId,
                isDeleted = false,
                deletedAt = null,
                deletedReason = null,
                updatedAt = System.currentTimeMillis(),
            )
            return if (updated == existing) existing else userRepository.save(updated)
        }

        val now = System.currentTimeMillis()
        return userRepository.save(
            User(
                username = username,
                displayName = displayName,
                avatarUrl = "",
                gender = 2,
                bio = "",
                passwordHash = EXTERNAL_TEMP_PASSWORD_HASH,
                inviteCode = "",
                myInviteCode = externalTemporaryInviteCode(credentialId, anonymousId),
                assignedCsUserId = customerServiceUserId,
                assignedCsQrCodeId = WebCustomerServiceRules.trimToNull(customerServiceQrCodeId),
                updatedAt = now,
            ),
        )
    }

    private fun toEntryResponse(entry: WebCustomerServiceEntry): WebCustomerServiceEntryResponse =
        WebCustomerServiceEntryResponse(
            id = entry.id.orEmpty(),
            name = entry.name,
            enabled = entry.enabled,
            allowedDomains = entry.allowedDomains,
            seatAdminIds = entry.seatAdminIds,
            welcomeMessage = entry.welcomeMessage,
            themeColor = entry.themeColor,
            createdBy = entry.createdBy,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )

    private fun workbenchContentType(contentType: Int): WebCustomerServiceContentType =
        if (contentType == ContentType.IMAGE.value) WebCustomerServiceContentType.IMAGE else WebCustomerServiceContentType.TEXT

    private fun workbenchContent(contentType: Int, content: String): String =
        when (contentType) {
            ContentType.TEXT.value -> content
            ContentType.IMAGE.value -> IMAGE_MESSAGE_CONTENT
            else -> "[消息]"
        }

    private fun preview(contentType: WebCustomerServiceContentType, content: String): String =
        if (contentType == WebCustomerServiceContentType.IMAGE) IMAGE_MESSAGE_CONTENT else content.take(80)

    private fun imageUrlFromContent(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return IMAGE_URL_PATTERN.find(trimmed)?.groupValues?.getOrNull(1)
    }

    private data class CustomerServiceContext(
        val user: User,
        val account: CustomerServiceAccount,
        val entry: WebCustomerServiceEntry,
        val displayName: String,
    )

    companion object {
        const val APP_ENTRY_ALLOWED_DOMAIN = "app.local"
        private const val CUSTOMER_SERVICE_ROLE = "CUSTOMER_SERVICE"
        private const val TEXT_MESSAGE_MAX_LENGTH = 5000
        private const val IMAGE_MESSAGE_CONTENT = "[图片]"
        private const val EXTERNAL_TEMP_PASSWORD_HASH = "external-temporary-account"
        private val IMAGE_URL_PATTERN = Regex(""""(?:url|imageUrl)"\s*:\s*"([^"]+)"""")

        fun appEntryId(customerServiceUserId: String): String =
            "app-customer-service-$customerServiceUserId"

        fun externalVisitorId(externalApiCredentialId: String, externalAnonymousId: String): String =
            "external:$externalApiCredentialId:$externalAnonymousId"

        fun externalTemporaryUsername(externalApiCredentialId: String, externalAnonymousId: String): String =
            "cs_tmp_${sha256Hex("$externalApiCredentialId:$externalAnonymousId").take(24)}"

        fun externalTemporaryInviteCode(externalApiCredentialId: String, externalAnonymousId: String): String =
            "CST${sha256Hex("invite:$externalApiCredentialId:$externalAnonymousId").take(13).uppercase()}"

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
