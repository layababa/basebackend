package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceCreateSessionRequest
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceInfo
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceMessagesResult
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceSessionResult
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceTextMessageRequest
import com.layababateam.xinxiwang_backend.dto.toWebCustomerServiceResponse
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrBinding
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class CustomerServiceExternalApiService(
    private val accountRepository: CustomerServiceAccountRepository,
    private val qrCodeRepository: CustomerServiceQrCodeRepository,
    private val bindingRepository: CustomerServiceQrBindingRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: WebCustomerServiceSessionRepository,
    private val messageRepository: WebCustomerServiceMessageRepository,
    private val workbenchService: CustomerServiceWorkbenchService,
) {
    fun createSession(
        credential: CustomerServiceExternalApiCredential,
        body: ExternalCustomerServiceCreateSessionRequest,
    ): ExternalCustomerServiceSessionResult {
        requireEnabled(credential)
        val anonymousId = normalizedAnonymousId(body.anonymousId)
        val current = sessionRepository
            .findFirstByExternalApiCredentialIdAndExternalAnonymousIdAndStatusNotOrderByLastMessageAtDesc(
                externalApiCredentialId = credential.id.orEmpty(),
                externalAnonymousId = anonymousId,
                status = WebCustomerServiceSessionStatus.CLOSED,
            )
        val customerServiceUserId = current?.assignedAdminId?.takeIf { it.isNotBlank() }
            ?: selectCustomerServiceUserId(credential)
        val message = workbenchService.recordExternalApiVisitorMessage(
            customerServiceUserId = customerServiceUserId,
            externalApiCredentialId = credential.id.orEmpty(),
            externalAnonymousId = anonymousId,
            visitorName = body.visitorName,
            sourceUrl = body.sourceUrl,
            content = body.content,
        )
        val session = sessionById(message.sessionId)
        return ExternalCustomerServiceSessionResult(
            session = session.toWebCustomerServiceResponse(),
            customerService = customerServiceInfo(customerServiceUserId),
            message = message.toWebCustomerServiceResponse(),
        )
    }

    fun sendMessage(
        credential: CustomerServiceExternalApiCredential,
        sessionId: String,
        body: ExternalCustomerServiceTextMessageRequest,
    ): ExternalCustomerServiceSessionResult {
        requireEnabled(credential)
        val session = ownedSession(credential, sessionId)
        val customerServiceUserId = session.assignedAdminId?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "customer service is not assigned")
        val message = workbenchService.recordExternalApiVisitorMessage(
            customerServiceUserId = customerServiceUserId,
            externalApiCredentialId = credential.id.orEmpty(),
            externalAnonymousId = session.externalAnonymousId.orEmpty(),
            visitorName = session.visitorName,
            sourceUrl = session.sourceUrl,
            content = body.content,
        )
        val updated = sessionById(message.sessionId)
        return ExternalCustomerServiceSessionResult(
            session = updated.toWebCustomerServiceResponse(),
            customerService = customerServiceInfo(customerServiceUserId),
            message = message.toWebCustomerServiceResponse(),
        )
    }

    fun messages(
        credential: CustomerServiceExternalApiCredential,
        sessionId: String,
        after: String?,
        size: Int,
    ): ExternalCustomerServiceMessagesResult {
        requireEnabled(credential)
        val session = ownedSession(credential, sessionId)
        val normalizedSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(0, normalizedSize)
        val messages = if (after.isNullOrBlank()) {
            messageRepository.findBySessionIdOrderByCreatedAtAsc(session.id.orEmpty(), pageable)
        } else {
            val cursor = messageRepository.findById(after.trim()).getOrNull()
                ?: throw NotFoundException("message not found")
            messageRepository.findBySessionIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                session.id.orEmpty(),
                cursor.createdAt,
                pageable,
            )
        }
        return ExternalCustomerServiceMessagesResult(
            session = session.toWebCustomerServiceResponse(),
            messages = messages.map { it.toWebCustomerServiceResponse() },
        )
    }

    private fun selectCustomerServiceUserId(credential: CustomerServiceExternalApiCredential): String {
        val qr = qrCodeRepository.findById(credential.qrCodeId).getOrNull()
            ?: throw NotFoundException("customer service qr not found")
        if (!qr.enabled) throw NotFoundException("customer service qr not available")
        val bindings = bindingRepository.findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc(qr.id.orEmpty())
        val accounts = accountRepository.findAllById(bindings.map { it.customerServiceId }).associateBy { it.id.orEmpty() }
        val users = usersById(accounts.values.map { it.userId })
        val selected = CustomerServiceQrAssignmentRules.selectBinding(
            bindings.map { binding ->
                val account = accounts[binding.customerServiceId]
                CustomerServiceQrCandidate(
                    bindingId = binding.id.orEmpty(),
                    customerServiceId = binding.customerServiceId,
                    assignedCount = binding.assignedCount,
                    sortOrder = binding.sortOrder,
                    createdAt = binding.createdAt,
                    bindingEnabled = binding.enabled,
                    accountEnabled = account?.let { it.enabled && isEligibleCustomerServiceUser(users[it.userId]) } == true,
                )
            },
        ) ?: throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "no available customer service account")
        val account = accounts[selected.customerServiceId] ?: throw NotFoundException("customer service account not found")
        return account.userId
    }

    private fun ownedSession(credential: CustomerServiceExternalApiCredential, sessionId: String): WebCustomerServiceSession {
        val session = sessionById(sessionId)
        if (session.externalApiCredentialId != credential.id.orEmpty()) {
            throw ForbiddenException("cannot access another credential session")
        }
        return session
    }

    private fun sessionById(sessionId: String): WebCustomerServiceSession =
        sessionRepository.findById(sessionId).getOrNull() ?: throw NotFoundException("customer service session not found")

    private fun customerServiceInfo(customerServiceUserId: String): ExternalCustomerServiceInfo {
        val account = accountRepository.findByUserId(customerServiceUserId)
        val user = userRepository.findByIdAndIsDeletedFalse(customerServiceUserId)
            ?: throw NotFoundException("customer service user not found")
        return ExternalCustomerServiceInfo(
            customerServiceId = account?.id.orEmpty(),
            customerServiceUserId = user.id.orEmpty(),
            displayName = WebCustomerServiceRules.trimToNull(account?.displayName)
                ?: WebCustomerServiceRules.trimToNull(user.displayName)
                ?: user.username,
            avatarUrl = user.avatarUrl,
            bio = user.bio,
        )
    }

    private fun requireEnabled(credential: CustomerServiceExternalApiCredential) {
        if (!credential.enabled) {
            throw ForbiddenException("external api credential disabled")
        }
        if (credential.id.isNullOrBlank()) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "external api credential id is required")
        }
        if (credential.qrCodeId.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "customer service qr is required")
        }
    }

    private fun normalizedAnonymousId(value: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "anonymousId is required")
        }
        return normalized
    }

    private fun usersById(userIds: Collection<String>): Map<String, User> =
        userRepository.findAllById(userIds.distinct()).associateBy { it.id.orEmpty() }

    private fun isEligibleCustomerServiceUser(user: User?): Boolean =
        user != null && !user.isDeleted
}
