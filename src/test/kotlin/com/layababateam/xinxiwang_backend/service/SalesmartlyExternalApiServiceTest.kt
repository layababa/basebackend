package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.controller.unusedMongoTemplate
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
import com.layababateam.xinxiwang_backend.repository.CustomerServiceExternalApiCredentialRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import org.springframework.data.domain.Pageable
import tools.jackson.databind.json.JsonMapper
import java.lang.reflect.Proxy
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SalesmartlyExternalApiServiceTest {
    @Test
    fun `rejects invalid project and invalid signature with compatible envelope codes`() {
        val state = SalesmartlyExternalApiState()
        val service = state.salesmartlyService()

        val invalidProject = service.handle(
            method = "GET",
            path = "/api/v2/get-member-list",
            queryParams = mapOf("project_id" to "missing", "page" to "1", "page_size" to "20"),
            headers = mapOf("external-sign" to "bad"),
        )
        val invalidSign = service.handle(
            method = "GET",
            path = "/api/v2/get-member-list",
            queryParams = mapOf("project_id" to "project-1", "page" to "1", "page_size" to "20"),
            headers = mapOf("external-sign" to "bad"),
        )

        assertEquals(100, invalidProject.code)
        assertEquals(102, invalidSign.code)
        assertNotNull(invalidSign.requestId)
    }

    @Test
    fun `runs P1 contact session message assign tag and end flow through workbench sessions`() {
        val state = SalesmartlyExternalApiState.withCustomerService()
        val service = state.salesmartlyService()

        val addParams = mapOf(
            "project_id" to "project-1",
            "channel" to "3",
            "from_channel_info" to "guest-1",
            "remark_name" to "Guest",
            "email" to "guest@example.com",
        )
        val add = service.handle("POST", "/api/v2/add-contact", bodyParams = addParams, headers = state.signed(addParams))
        assertEquals(0, add.code)
        val contact = add.dataMap()
        val chatUserId = contact.string("chat_user_id")

        val memberParams = mapOf("project_id" to "project-1", "page" to "1", "page_size" to "20")
        val member = service.handle("GET", "/api/v2/get-member-list", queryParams = memberParams, headers = state.signed(memberParams))
        val sysUserId = member.dataMap().list("list").first().string("sys_user_id")

        val sessionParams = mapOf("project_id" to "project-1", "page" to "1", "page_size" to "20")
        val sessions = service.handle("GET", "/api/v2/get-session-list", queryParams = sessionParams, headers = state.signed(sessionParams))
        val session = sessions.dataMap().list("list").first()
        val sessionId = session.string("session_id")
        assertEquals(chatUserId, session.string("chat_user_id"))

        val reply = state.workbenchService().sendText("cs-1", sessionId, "reply")
        assertEquals(WebCustomerServiceSenderType.ADMIN, reply.senderType)

        val messageParams = mapOf("project_id" to "project-1", "page_size" to "20", "chat_user_id" to chatUserId)
        val messages = service.handle("GET", "/api/v2/get-message-list", queryParams = messageParams, headers = state.signed(messageParams))
        assertEquals(listOf("Conversation created.", "reply"), messages.dataMap().list("list").map { it.string("text") })

        val assignParams = mapOf(
            "project_id" to "project-1",
            "session_id" to sessionId,
            "chat_user_id" to chatUserId,
            "sys_user_id" to sysUserId,
            "assign_sys_user_id" to sysUserId,
        )
        assertEquals(0, service.handle("POST", "/api/v2/assign-chat-user", bodyParams = assignParams, headers = state.signed(assignParams)).code)

        val tagParams = mapOf("project_id" to "project-1", "session_id" to sessionId, "tag_ids" to "vip,paid")
        assertEquals(0, service.handle("POST", "/api/v2/tag-session", bodyParams = tagParams, headers = state.signed(tagParams)).code)
        assertEquals(true, state.sessions[sessionId]?.salesmartlyTagsJson?.contains("vip"))

        val endParams = mapOf("project_id" to "project-1", "session_id" to sessionId, "chat_user_id" to chatUserId)
        assertEquals(0, service.handle("POST", "/api/v2/end-session", bodyParams = endParams, headers = state.signed(endParams)).code)

        val endedParams = mapOf("project_id" to "project-1", "page" to "1", "page_size" to "20", "session_status" to "1")
        val ended = service.handle("GET", "/api/v2/get-session-list", queryParams = endedParams, headers = state.signed(endedParams))
        assertTrue(ended.dataMap().list("list").first().int("end_time") > 0)
    }

    @Test
    fun `limits qps per project route`() {
        val state = SalesmartlyExternalApiState.withCustomerService()
        val service = state.salesmartlyService()
        val params = mapOf("project_id" to "project-1", "page" to "1", "page_size" to "20")

        repeat(10) {
            assertEquals(0, service.handle("GET", "/api/v2/get-member-list", queryParams = params, headers = state.signed(params)).code)
        }

        assertEquals(
            103,
            service.handle("GET", "/api/v2/get-member-list", queryParams = params, headers = state.signed(params)).code,
        )
    }
}

private data class SalesmartlyExternalApiState(
    val users: MutableMap<String, User> = linkedMapOf(),
    val credentials: MutableMap<String, CustomerServiceExternalApiCredential> = linkedMapOf(
        "credential-1" to CustomerServiceExternalApiCredential(
            id = "credential-1",
            name = "Salesmartly",
            apiKey = "project-1",
            apiSecret = "token-1",
            qrCodeId = "qr-1",
            enabled = true,
            createdBy = "admin-1",
        ),
    ),
    val accounts: MutableMap<String, CustomerServiceAccount> = linkedMapOf(),
    val qrs: MutableMap<String, CustomerServiceQrCode> = linkedMapOf(),
    val bindings: MutableMap<String, CustomerServiceQrBinding> = linkedMapOf(),
    val entries: MutableMap<String, WebCustomerServiceEntry> = linkedMapOf(),
    val sessions: MutableMap<String, WebCustomerServiceSession> = linkedMapOf(),
    val messages: MutableMap<String, WebCustomerServiceMessage> = linkedMapOf(),
) {
    fun salesmartlyService() = SalesmartlyExternalApiService(
        credentialRepository = credentialRepository(),
        accountRepository = accountRepository(),
        qrCodeRepository = qrRepository(),
        bindingRepository = bindingRepository(),
        userRepository = userRepository(),
        sessionRepository = sessionRepository(),
        messageRepository = messageRepository(),
        workbenchService = workbenchService(),
        objectMapper = JsonMapper.builder().build(),
        signature = SalesmartlyExternalApiSignature(),
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
            uploadPort = salesmartlyProxy(UploadPort::class.java) { method, _ -> salesmartlyDefaultValue(method.returnType) },
            mongoTemplate = unusedMongoTemplate(),
        ),
    )

    fun signed(params: Map<String, Any?>): Map<String, String> =
        mapOf("external-sign" to SalesmartlyExternalApiSignature().sign("token-1", params))

    private fun credentialRepository(): CustomerServiceExternalApiCredentialRepository =
        salesmartlyProxy(CustomerServiceExternalApiCredentialRepository::class.java) { method, args ->
            when (method.name) {
                "findByApiKey" -> credentials.values.firstOrNull { it.apiKey == args?.firstOrNull() }
                "findById" -> Optional.ofNullable(credentials[args?.firstOrNull() as String])
                "save" -> {
                    val credential = args?.firstOrNull() as CustomerServiceExternalApiCredential
                    credentials[credential.id.orEmpty()] = credential
                    credential
                }
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun accountRepository(): CustomerServiceAccountRepository =
        salesmartlyProxy(CustomerServiceAccountRepository::class.java) { method, args ->
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
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun qrRepository(): CustomerServiceQrCodeRepository =
        salesmartlyProxy(CustomerServiceQrCodeRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(qrs[args?.firstOrNull() as String])
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun bindingRepository(): CustomerServiceQrBindingRepository =
        salesmartlyProxy(CustomerServiceQrBindingRepository::class.java) { method, args ->
            when (method.name) {
                "findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc" ->
                    bindings.values.filter { it.qrCodeId == args?.firstOrNull() as String }
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun userRepository(): UserRepository =
        salesmartlyProxy(UserRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(users[args?.firstOrNull() as String])
                "findByIdAndIsDeletedFalse" -> users[args?.firstOrNull() as String]?.takeUnless { it.isDeleted }
                "findAllById" -> {
                    val ids = (args?.firstOrNull() as Iterable<*>).map { it.toString() }.toSet()
                    users.values.filter { it.id in ids }
                }
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun entryRepository(): WebCustomerServiceEntryRepository =
        salesmartlyProxy(WebCustomerServiceEntryRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(entries[args?.firstOrNull() as String])
                "save" -> {
                    val entry = args?.firstOrNull() as WebCustomerServiceEntry
                    entries[entry.id.orEmpty()] = entry
                    entry
                }
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun sessionRepository(): WebCustomerServiceSessionRepository =
        salesmartlyProxy(WebCustomerServiceSessionRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(sessions[args?.firstOrNull() as String])
                "findByExternalApiCredentialIdOrderByLastMessageAtDesc" ->
                    sessions.values
                        .filter { it.externalApiCredentialId == args?.firstOrNull() as String }
                        .sortedByDescending { it.lastMessageAt }
                "findFirstByExternalApiCredentialIdAndExternalAnonymousIdAndStatusNotOrderByLastMessageAtDesc" -> {
                    val credentialId = args?.getOrNull(0) as String
                    val anonymousId = args.getOrNull(1) as String
                    val status = args.getOrNull(2) as WebCustomerServiceSessionStatus
                    sessions.values
                        .filter { it.externalApiCredentialId == credentialId && it.externalAnonymousId == anonymousId && it.status != status }
                        .maxByOrNull { it.lastMessageAt }
                }
                "save" -> {
                    val session = args?.firstOrNull() as WebCustomerServiceSession
                    val saved = if (session.id == null) session.copy(id = "session-${sessions.size + 1}") else session
                    sessions[saved.id.orEmpty()] = saved
                    saved
                }
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    private fun messageRepository(): WebCustomerServiceMessageRepository =
        salesmartlyProxy(WebCustomerServiceMessageRepository::class.java) { method, args ->
            when (method.name) {
                "findBySessionIdOrderByCreatedAtAsc" -> {
                    val sessionId = args?.getOrNull(0) as String
                    val pageable = args.getOrNull(1) as Pageable
                    messages.values.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }.take(pageable.pageSize)
                }
                "save" -> {
                    val message = args?.firstOrNull() as WebCustomerServiceMessage
                    val saved = if (message.id == null) message.copy(id = "message-${messages.size + 1}", createdAt = 1000L + messages.size) else message
                    messages[saved.id.orEmpty()] = saved
                    saved
                }
                else -> salesmartlyDefaultValue(method.returnType)
            }
        }

    companion object {
        fun withCustomerService() = SalesmartlyExternalApiState(
            users = linkedMapOf(
                "cs-1" to salesmartlyUser("cs-1", "cs01", "Support", isOperator = true),
            ),
            accounts = linkedMapOf(
                "account-1" to CustomerServiceAccount(id = "account-1", userId = "cs-1", displayName = "Support"),
            ),
            qrs = linkedMapOf(
                "qr-1" to CustomerServiceQrCode(id = "qr-1", name = "API", code = "api-code", enabled = true),
            ),
            bindings = linkedMapOf(
                "binding-1" to CustomerServiceQrBinding(id = "binding-1", qrCodeId = "qr-1", customerServiceId = "account-1", enabled = true),
            ),
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.dataMap(): Map<String, Any?> = this["data"] as Map<String, Any?>

private fun SalesmartlyExternalApiResponse.dataMap(): Map<String, Any?> = data as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> = this[key] as List<Map<String, Any?>>

private fun Map<String, Any?>.string(key: String): String = this[key].toString()

private fun Map<String, Any?>.int(key: String): Int = this[key].toString().toInt()

private fun salesmartlyUser(id: String, username: String, displayName: String, isOperator: Boolean) = User(
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

@Suppress("UNCHECKED_CAST")
private fun <T> salesmartlyProxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) } as T

private fun salesmartlyDefaultValue(type: Class<*>): Any? =
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
