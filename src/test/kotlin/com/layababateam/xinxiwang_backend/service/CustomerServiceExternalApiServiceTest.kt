package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.controller.unusedMongoTemplate
import com.layababateam.xinxiwang_backend.dto.ExternalCustomerServiceCreateSessionRequest
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrBinding
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrCode
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceEntry
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceMessage
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSenderType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import org.springframework.data.domain.Pageable
import java.lang.reflect.Proxy
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomerServiceExternalApiServiceTest {
    @Test
    fun `external api creates anonymous session and polling includes customer service reply`() {
        val state = ExternalApiState(
            users = linkedMapOf(
                "cs-1" to externalUser("cs-1", "cs01", "Support", isOperator = true),
            ),
            accounts = linkedMapOf(
                "account-1" to CustomerServiceAccount(id = "account-1", userId = "cs-1", displayName = "Support"),
            ),
            qrs = linkedMapOf(
                "qr-1" to CustomerServiceQrCode(id = "qr-1", name = "API", code = "api-code", enabled = true),
            ),
            bindings = linkedMapOf(
                "binding-1" to CustomerServiceQrBinding(
                    id = "binding-1",
                    qrCodeId = "qr-1",
                    customerServiceId = "account-1",
                    enabled = true,
                ),
            ),
        )
        val credential = CustomerServiceExternalApiCredential(
            id = "credential-1",
            name = "Partner API",
            apiKey = "key-1",
            apiSecret = "secret-1",
            qrCodeId = "qr-1",
            enabled = true,
            createdBy = "admin-1",
        )
        val service = state.externalService()

        val created = service.createSession(
            credential = credential,
            body = ExternalCustomerServiceCreateSessionRequest(
                anonymousId = "visitor-1",
                visitorName = "Guest",
                content = "hello",
                sourceUrl = "https://partner.example/help",
            ),
        )
        val reply = state.workbenchService().sendText(
            customerServiceUserId = "cs-1",
            sessionId = created.session.id,
            content = "reply",
        )
        val polled = service.messages(
            credential = credential,
            sessionId = created.session.id,
            after = null,
            size = 50,
        )

        assertEquals(WebCustomerServiceSenderType.ADMIN, reply.senderType)
        assertEquals("cs-1", created.customerService.customerServiceUserId)
        assertEquals(listOf("hello", "reply"), polled.messages.map { it.content })
        assertEquals("credential-1", state.sessions[created.session.id]?.externalApiCredentialId)
        assertEquals("visitor-1", state.sessions[created.session.id]?.externalAnonymousId)
    }
}

private data class ExternalApiState(
    val users: MutableMap<String, User> = linkedMapOf(),
    val accounts: MutableMap<String, CustomerServiceAccount> = linkedMapOf(),
    val qrs: MutableMap<String, CustomerServiceQrCode> = linkedMapOf(),
    val bindings: MutableMap<String, CustomerServiceQrBinding> = linkedMapOf(),
    val entries: MutableMap<String, WebCustomerServiceEntry> = linkedMapOf(),
    val sessions: MutableMap<String, WebCustomerServiceSession> = linkedMapOf(),
    val messages: MutableMap<String, WebCustomerServiceMessage> = linkedMapOf(),
) {
    fun externalService() = CustomerServiceExternalApiService(
        accountRepository = accountRepository(),
        qrCodeRepository = qrRepository(),
        bindingRepository = bindingRepository(),
        userRepository = userRepository(),
        sessionRepository = sessionRepository(),
        messageRepository = messageRepository(),
        workbenchService = workbenchService(),
    )

    fun workbenchService() = CustomerServiceWorkbenchService(
        accountRepository = accountRepository(),
        userRepository = userRepository(),
        entryRepository = entryRepository(),
        sessionRepository = sessionRepository(),
        messageRepository = messageRepository(),
        webCustomerServiceService = WebCustomerServiceService(
            entryRepository = entryRepository(),
            sessionRepository = sessionRepository(),
            messageRepository = messageRepository(),
            tokenService = WebCustomerServiceTokenService(),
            uploadPort = externalUnsupportedProxy(UploadPort::class.java),
            mongoTemplate = unusedMongoTemplate(),
        ),
    )

    private fun accountRepository(): CustomerServiceAccountRepository =
        externalProxy(CustomerServiceAccountRepository::class.java) { method, args ->
            when (method.name) {
                "findByUserId" -> accounts.values.firstOrNull { it.userId == args?.firstOrNull() }
                "findAllById" -> {
                    val ids = (args?.firstOrNull() as Iterable<*>).map { it.toString() }.toSet()
                    accounts.values.filter { it.id in ids }
                }
                "save" -> {
                    val account = args?.firstOrNull() as CustomerServiceAccount
                    val saved = if (account.id == null) account.copy(id = "account-${accounts.size + 1}") else account
                    accounts[saved.id.orEmpty()] = saved
                    saved
                }
                else -> externalDefaultValue(method.returnType)
            }
        }

    private fun qrRepository(): CustomerServiceQrCodeRepository =
        externalProxy(CustomerServiceQrCodeRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(qrs[args?.firstOrNull() as String])
                else -> externalDefaultValue(method.returnType)
            }
        }

    private fun bindingRepository(): CustomerServiceQrBindingRepository =
        externalProxy(CustomerServiceQrBindingRepository::class.java) { method, args ->
            when (method.name) {
                "findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc" ->
                    bindings.values.filter { it.qrCodeId == args?.firstOrNull() as String }
                else -> externalDefaultValue(method.returnType)
            }
        }

    private fun userRepository(): UserRepository =
        externalProxy(UserRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(users[args?.firstOrNull() as String])
                "findByIdAndIsDeletedFalse" -> users[args?.firstOrNull() as String]?.takeUnless { it.isDeleted }
                "findAllById" -> {
                    val ids = (args?.firstOrNull() as Iterable<*>).map { it.toString() }.toSet()
                    users.values.filter { it.id in ids }
                }
                else -> externalDefaultValue(method.returnType)
            }
        }

    private fun entryRepository(): WebCustomerServiceEntryRepository =
        externalProxy(WebCustomerServiceEntryRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(entries[args?.firstOrNull() as String])
                "save" -> {
                    val entry = args?.firstOrNull() as WebCustomerServiceEntry
                    entries[entry.id.orEmpty()] = entry
                    entry
                }
                else -> externalDefaultValue(method.returnType)
            }
        }

    private fun sessionRepository(): WebCustomerServiceSessionRepository =
        externalProxy(WebCustomerServiceSessionRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(sessions[args?.firstOrNull() as String])
                "findFirstByExternalApiCredentialIdAndExternalAnonymousIdAndStatusNotOrderByLastMessageAtDesc" -> {
                    val credentialId = args?.getOrNull(0) as String
                    val anonymousId = args.getOrNull(1) as String
                    val status = args.getOrNull(2) as WebCustomerServiceSessionStatus
                    sessions.values
                        .filter {
                            it.externalApiCredentialId == credentialId &&
                                it.externalAnonymousId == anonymousId &&
                                it.status != status
                        }
                        .maxByOrNull { it.lastMessageAt }
                }
                "save" -> {
                    val session = args?.firstOrNull() as WebCustomerServiceSession
                    val saved = if (session.id == null) session.copy(id = "session-${sessions.size + 1}") else session
                    sessions[saved.id.orEmpty()] = saved
                    saved
                }
                else -> externalDefaultValue(method.returnType)
            }
        }

    private fun messageRepository(): WebCustomerServiceMessageRepository =
        externalProxy(WebCustomerServiceMessageRepository::class.java) { method, args ->
            when (method.name) {
                "findBySessionIdOrderByCreatedAtAsc" -> {
                    val sessionId = args?.getOrNull(0) as String
                    val pageable = args.getOrNull(1) as Pageable
                    messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }.take(pageable.pageSize)
                }
                "save" -> {
                    val message = args?.firstOrNull() as WebCustomerServiceMessage
                    val saved = if (message.id == null) {
                        message.copy(id = "message-${messages.size + 1}", createdAt = 100L + messages.size)
                    } else {
                        message
                    }
                    messages[saved.id.orEmpty()] = saved
                    saved
                }
                else -> externalDefaultValue(method.returnType)
            }
        }
}

private fun externalUser(id: String, username: String, displayName: String, isOperator: Boolean) = User(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = "",
    gender = 0,
    bio = "",
    passwordHash = "hash",
    inviteCode = "",
    myInviteCode = "invite-$id",
    isOperator = isOperator,
)

private fun <T> externalProxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) } as T

private fun <T> externalUnsupportedProxy(type: Class<T>): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> externalDefaultValue(method.returnType) } as T

private fun externalDefaultValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        Optional::class.java -> Optional.empty<Any>()
        List::class.java -> emptyList<Any>()
        Iterable::class.java -> emptyList<Any>()
        else -> null
    }
