package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.CustomerServiceAccountRequest
import com.layababateam.xinxiwang_backend.dto.CustomerServiceAccountResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrApplyResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrBindingResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrBindingsUpdateRequest
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrCodeRequest
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrCodeResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceQrDetailResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceSummary
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrBinding
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrCode
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class CustomerServiceQrAssignmentService(
    private val accountRepository: CustomerServiceAccountRepository,
    private val qrCodeRepository: CustomerServiceQrCodeRepository,
    private val bindingRepository: CustomerServiceQrBindingRepository,
    private val userRepository: UserRepository,
    private val mongoTemplate: MongoTemplate,
    private val friendPortProvider: ObjectProvider<CustomerServiceFriendPort>,
    @Value("\${xinxiwang.customer-service-qr.public-base-url:}")
    private val configuredPublicBaseUrl: String,
) {
    private val assignmentLock = Any()

    fun listAccounts(keyword: String?, enabled: Boolean?): List<CustomerServiceAccountResponse> {
        val accounts = accountRepository.findAllByOrderBySortOrderAscCreatedAtAsc()
            .filter { enabled == null || it.enabled == enabled }
        val users = usersById(accounts.map { it.userId })
        val normalizedKeyword = keyword?.trim()?.lowercase().takeUnless { it.isNullOrBlank() }
        return accounts
            .map { toAccountResponse(it, users[it.userId]) }
            .filter { response ->
                normalizedKeyword == null ||
                    response.username.lowercase().contains(normalizedKeyword) ||
                    response.displayName.lowercase().contains(normalizedKeyword) ||
                    response.userId.lowercase().contains(normalizedKeyword)
            }
    }

    fun createAccount(request: CustomerServiceAccountRequest): CustomerServiceAccountResponse {
        val user = customerServiceUser(request.userId)
        if (accountRepository.findByUserId(user.id.orEmpty()) != null) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "customer service account already exists")
        }
        val now = System.currentTimeMillis()
        val saved = accountRepository.save(
            CustomerServiceAccount(
                userId = user.id.orEmpty(),
                displayName = trimToNull(request.displayName),
                remark = trimToNull(request.remark),
                sortOrder = request.sortOrder,
                enabled = request.enabled,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return toAccountResponse(saved, user)
    }

    fun updateAccount(id: String, request: CustomerServiceAccountRequest): CustomerServiceAccountResponse {
        val current = accountById(id)
        val user = customerServiceUser(request.userId)
        val duplicate = accountRepository.findByUserId(user.id.orEmpty())
        if (duplicate != null && duplicate.id != id) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "customer service account already exists")
        }
        val saved = accountRepository.save(
            current.copy(
                userId = user.id.orEmpty(),
                displayName = trimToNull(request.displayName),
                remark = trimToNull(request.remark),
                sortOrder = request.sortOrder,
                enabled = request.enabled,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return toAccountResponse(saved, user)
    }

    fun listQrCodes(request: HttpServletRequest): List<CustomerServiceQrCodeResponse> =
        qrCodeRepository.findAllByOrderByCreatedAtDesc().map { qr ->
            toQrResponse(qr, request, bindingRepository.findByQrCodeId(qr.id.orEmpty()))
        }

    fun createQrCode(body: CustomerServiceQrCodeRequest, request: HttpServletRequest, adminId: String?): CustomerServiceQrCodeResponse {
        val now = System.currentTimeMillis()
        val saved = qrCodeRepository.save(
            CustomerServiceQrCode(
                name = body.name.trim(),
                code = UUID.randomUUID().toString().replace("-", ""),
                remark = trimToNull(body.remark),
                enabled = body.enabled,
                createdBy = adminId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return toQrResponse(saved, request, emptyList())
    }

    fun updateQrCode(id: String, body: CustomerServiceQrCodeRequest, request: HttpServletRequest): CustomerServiceQrCodeResponse {
        val current = qrById(id)
        val saved = qrCodeRepository.save(
            current.copy(
                name = body.name.trim(),
                remark = trimToNull(body.remark),
                enabled = body.enabled,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return toQrResponse(saved, request, bindingRepository.findByQrCodeId(id))
    }

    fun qrDetail(id: String, request: HttpServletRequest): CustomerServiceQrDetailResponse {
        val qr = qrById(id)
        val bindings = bindingRepository.findByQrCodeId(id)
            .sortedWith(compareBy<CustomerServiceQrBinding> { it.sortOrder }.thenBy { it.createdAt })
        return CustomerServiceQrDetailResponse(
            qr = toQrResponse(qr, request, bindings),
            bindings = toBindingResponses(bindings),
        )
    }

    fun replaceBindings(
        qrCodeId: String,
        body: CustomerServiceQrBindingsUpdateRequest,
        request: HttpServletRequest,
    ): CustomerServiceQrDetailResponse {
        qrById(qrCodeId)
        val requested = body.bindings.distinctBy { it.customerServiceId.trim() }
        val accounts = accountRepository.findAllById(requested.map { it.customerServiceId.trim() }).associateBy { it.id.orEmpty() }
        requested.forEach {
            if (accounts[it.customerServiceId.trim()] == null) {
                throw NotFoundException("customer service account not found")
            }
        }

        val existing = bindingRepository.findByQrCodeId(qrCodeId)
        val existingByAccount = existing.associateBy { it.customerServiceId }
        val requestedIds = requested.map { it.customerServiceId.trim() }.toSet()
        val now = System.currentTimeMillis()

        requested.forEach { item ->
            val accountId = item.customerServiceId.trim()
            val current = existingByAccount[accountId]
            if (current == null) {
                bindingRepository.save(
                    CustomerServiceQrBinding(
                        qrCodeId = qrCodeId,
                        customerServiceId = accountId,
                        enabled = item.enabled,
                        sortOrder = item.sortOrder,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                bindingRepository.save(
                    current.copy(
                        enabled = item.enabled,
                        sortOrder = item.sortOrder,
                        updatedAt = now,
                    ),
                )
            }
        }

        existing
            .filter { it.customerServiceId !in requestedIds && it.enabled }
            .forEach { bindingRepository.save(it.copy(enabled = false, updatedAt = now)) }

        return qrDetail(qrCodeId, request)
    }

    fun apply(code: String, scannerUserId: String): CustomerServiceQrApplyResponse =
        synchronized(assignmentLock) {
            applyLocked(code.trim(), scannerUserId)
        }

    fun releaseForDeletedUser(userId: String) {
        synchronized(assignmentLock) {
            val user = userRepository.findById(userId).getOrNull() ?: return
            val assignedCsUserId = user.assignedCsUserId ?: return
            val qrCodeId = user.assignedCsQrCodeId ?: return
            val account = accountRepository.findByUserId(assignedCsUserId) ?: return
            val binding = bindingRepository.findByQrCodeIdAndCustomerServiceId(qrCodeId, account.id.orEmpty()) ?: return
            rollbackAssignment(userId, assignedCsUserId, qrCodeId, binding.id.orEmpty(), account.id.orEmpty())
        }
    }

    private fun applyLocked(code: String, scannerUserId: String): CustomerServiceQrApplyResponse {
        if (code.isBlank()) throw BusinessException(ErrorCode.INVALID_PARAM, "code is required")
        val scanner = userRepository.findByIdAndIsDeletedFalse(scannerUserId) ?: throw NotFoundException("user not found")
        if (scanner.isOperator) throw ForbiddenException("customer service account cannot scan customer service qr")
        val alreadyAssigned = scanner.assignedCsUserId
        if (!alreadyAssigned.isNullOrBlank()) {
            return assignedResponse(alreadyAssigned, scanner.assignedCsQrCodeId.orEmpty(), true)
        }

        val qr = qrCodeRepository.findByCode(code) ?: throw NotFoundException("customer service qr not found")
        if (!qr.enabled) throw NotFoundException("customer service qr not available")
        val qrId = qr.id.orEmpty()
        val bindings = bindingRepository.findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc(qrId)
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

        val binding = bindings.first { it.id.orEmpty() == selected.bindingId }
        val account = accounts[selected.customerServiceId] ?: throw NotFoundException("customer service account not found")
        val customerServiceUser = users[account.userId] ?: throw NotFoundException("customer service user not found")
        assignUserOrReturnExisting(scannerUserId, account.userId, qrId)?.let { existingUser ->
            return assignedResponse(existingUser.assignedCsUserId.orEmpty(), existingUser.assignedCsQrCodeId.orEmpty(), true)
        }

        incrementCounts(qrId, binding.id.orEmpty(), account.id.orEmpty(), 1)
        try {
            val friendPort = friendPortProvider.getIfAvailable()
                ?: throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "customer service friend port is not configured")
            friendPort.ensureCustomerServiceFriendship(scannerUserId, customerServiceUser.id.orEmpty())
        } catch (e: RuntimeException) {
            rollbackAssignment(scannerUserId, account.userId, qrId, binding.id.orEmpty(), account.id.orEmpty())
            throw e
        }

        return CustomerServiceQrApplyResponse(
            alreadyAssigned = false,
            qrCodeId = qrId,
            customerServiceId = account.id.orEmpty(),
            customerServiceUserId = account.userId,
            customerService = toSummary(customerServiceUser),
        )
    }

    private fun assignUserOrReturnExisting(scannerUserId: String, customerServiceUserId: String, qrCodeId: String): User? {
        val criteria = Criteria().andOperator(
            Criteria.where("_id").`is`(scannerUserId),
            Criteria.where("isDeleted").ne(true),
            Criteria().orOperator(
                Criteria.where("assignedCsUserId").exists(false),
                Criteria.where("assignedCsUserId").`is`(null),
                Criteria.where("assignedCsUserId").`is`(""),
            ),
        )
        val updated = mongoTemplate.findAndModify(
            Query(criteria),
            Update()
                .set("assignedCsUserId", customerServiceUserId)
                .set("assignedCsQrCodeId", qrCodeId)
                .set("updatedAt", System.currentTimeMillis()),
            FindAndModifyOptions.options().returnNew(true),
            User::class.java,
        )
        return if (updated == null) userRepository.findByIdAndIsDeletedFalse(scannerUserId) else null
    }

    private fun assignedResponse(customerServiceUserId: String, qrCodeId: String, alreadyAssigned: Boolean): CustomerServiceQrApplyResponse {
        val account = accountRepository.findByUserId(customerServiceUserId) ?: throw NotFoundException("customer service account not found")
        val customerServiceUser = userRepository.findByIdAndIsDeletedFalse(customerServiceUserId)
            ?: throw NotFoundException("customer service user not found")
        return CustomerServiceQrApplyResponse(
            alreadyAssigned = alreadyAssigned,
            qrCodeId = qrCodeId,
            customerServiceId = account.id.orEmpty(),
            customerServiceUserId = customerServiceUser.id.orEmpty(),
            customerService = toSummary(customerServiceUser),
        )
    }

    private fun rollbackAssignment(
        userId: String,
        customerServiceUserId: String,
        qrCodeId: String,
        bindingId: String,
        accountId: String,
    ) {
        mongoTemplate.updateFirst(
            Query(
                Criteria.where("_id").`is`(userId)
                    .and("assignedCsUserId").`is`(customerServiceUserId)
                    .and("assignedCsQrCodeId").`is`(qrCodeId),
            ),
            Update()
                .unset("assignedCsUserId")
                .unset("assignedCsQrCodeId")
                .set("updatedAt", System.currentTimeMillis()),
            User::class.java,
        )
        incrementCounts(qrCodeId, bindingId, accountId, -1)
    }

    private fun incrementCounts(qrCodeId: String, bindingId: String, accountId: String, delta: Long) {
        val now = System.currentTimeMillis()
        mongoTemplate.updateFirst(Query(Criteria.where("_id").`is`(qrCodeId)), Update().inc("assignedCount", delta).set("updatedAt", now), CustomerServiceQrCode::class.java)
        mongoTemplate.updateFirst(Query(Criteria.where("_id").`is`(bindingId)), Update().inc("assignedCount", delta).set("updatedAt", now), CustomerServiceQrBinding::class.java)
        mongoTemplate.updateFirst(Query(Criteria.where("_id").`is`(accountId)), Update().inc("assignedCount", delta).set("updatedAt", now), CustomerServiceAccount::class.java)
    }

    private fun toBindingResponses(bindings: List<CustomerServiceQrBinding>): List<CustomerServiceQrBindingResponse> {
        val accounts = accountRepository.findAllById(bindings.map { it.customerServiceId }).associateBy { it.id.orEmpty() }
        val users = usersById(accounts.values.map { it.userId })
        return bindings.map { binding ->
            val account = accounts[binding.customerServiceId]
            CustomerServiceQrBindingResponse(
                id = binding.id.orEmpty(),
                qrCodeId = binding.qrCodeId,
                customerServiceId = binding.customerServiceId,
                enabled = binding.enabled,
                sortOrder = binding.sortOrder,
                assignedCount = binding.assignedCount,
                customerService = account?.let { toAccountResponse(it, users[it.userId]) },
                createdAt = binding.createdAt,
                updatedAt = binding.updatedAt,
            )
        }
    }

    private fun toQrResponse(
        qr: CustomerServiceQrCode,
        request: HttpServletRequest,
        bindings: List<CustomerServiceQrBinding>,
    ): CustomerServiceQrCodeResponse =
        CustomerServiceQrCodeResponse(
            id = qr.id.orEmpty(),
            name = qr.name,
            code = qr.code,
            qrUrl = CustomerServiceQrAssignmentRules.buildQrUrl(publicBaseUrl(request), qr.code),
            remark = qr.remark,
            enabled = qr.enabled,
            assignedCount = qr.assignedCount,
            bindingCount = bindings.count { it.enabled },
            createdBy = qr.createdBy,
            createdAt = qr.createdAt,
            updatedAt = qr.updatedAt,
        )

    private fun toAccountResponse(account: CustomerServiceAccount, user: User?): CustomerServiceAccountResponse =
        CustomerServiceAccountResponse(
            id = account.id.orEmpty(),
            userId = account.userId,
            username = user?.username.orEmpty(),
            displayName = account.displayName ?: user?.displayName.orEmpty(),
            avatarUrl = user?.avatarUrl.orEmpty(),
            gender = user?.gender ?: 2,
            bio = user?.bio.orEmpty(),
            remark = account.remark,
            sortOrder = account.sortOrder,
            enabled = account.enabled,
            assignedCount = account.assignedCount,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
        )

    private fun toSummary(user: User): CustomerServiceSummary =
        CustomerServiceSummary(
            userId = user.id.orEmpty(),
            username = user.username,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            gender = user.gender,
            bio = user.bio,
        )

    private fun accountById(id: String): CustomerServiceAccount =
        accountRepository.findById(id).getOrNull() ?: throw NotFoundException("customer service account not found")

    private fun qrById(id: String): CustomerServiceQrCode =
        qrCodeRepository.findById(id).getOrNull() ?: throw NotFoundException("customer service qr not found")

    private fun customerServiceUser(userId: String): User =
        userRepository.findByIdAndIsDeletedFalse(userId.trim()) ?: throw NotFoundException("user not found")

    private fun isEligibleCustomerServiceUser(user: User?): Boolean =
        user != null && !user.isDeleted

    private fun usersById(userIds: Collection<String>): Map<String, User> =
        userRepository.findAllById(userIds.distinct()).associateBy { it.id.orEmpty() }

    private fun trimToNull(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() }

    private fun publicBaseUrl(request: HttpServletRequest): String =
        configuredPublicBaseUrl.trim().takeIf { it.isNotBlank() } ?: externalBaseUrl(request)

    private fun externalBaseUrl(request: HttpServletRequest): String {
        val proto = request.getHeader("X-Forwarded-Proto")?.substringBefore(',')?.trim().takeUnless { it.isNullOrBlank() }
            ?: request.scheme
        val host = request.getHeader("X-Forwarded-Host")?.substringBefore(',')?.trim().takeUnless { it.isNullOrBlank() }
            ?: request.serverName + if (request.serverPort in setOf(80, 443)) "" else ":${request.serverPort}"
        return "$proto://$host"
    }
}
