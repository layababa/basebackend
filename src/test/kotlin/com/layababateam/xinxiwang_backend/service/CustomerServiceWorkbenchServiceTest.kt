package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.controller.unusedMongoTemplate
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
import java.lang.reflect.Proxy
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CustomerServiceWorkbenchServiceTest {
    @Test
    fun `profile auto creates app entry with logged in customer service as the only seat`() {
        val state = WorkbenchState(
            users = linkedMapOf("cs-1" to user("cs-1", "cs01", "CS One", isOperator = true)),
            accounts = linkedMapOf("account-1" to account("account-1", "cs-1")),
        )
        val service = state.service()

        val profile = service.profile("cs-1")

        val entry = state.entries[profile.entry.id]
        assertNotNull(entry)
        assertEquals("cs-1", profile.customerServiceUserId)
        assertEquals(listOf("cs-1"), entry.seatAdminIds)
        assertEquals(listOf(CustomerServiceWorkbenchService.APP_ENTRY_ALLOWED_DOMAIN), entry.allowedDomains)
    }

    @Test
    fun `assigned customer private message is recorded in the app workbench session`() {
        val state = WorkbenchState(
            users = linkedMapOf(
                "customer-1" to user("customer-1", "alice", "Alice", assignedCsUserId = "cs-1"),
                "cs-1" to user("cs-1", "cs01", "CS One", isOperator = true),
            ),
            accounts = linkedMapOf("account-1" to account("account-1", "cs-1")),
        )
        val service = state.service()

        val recorded = service.recordAssignedCustomerMessage(
            customerUserId = "customer-1",
            customerServiceUserId = "cs-1",
            contentType = 0,
            content = "hello",
        )

        assertNotNull(recorded)
        assertEquals(WebCustomerServiceSenderType.VISITOR, recorded.senderType)
        assertEquals(WebCustomerServiceContentType.TEXT, recorded.contentType)
        assertEquals("hello", recorded.content)
        assertEquals("customer-1", recorded.senderId)
        val session = state.sessions.values.single()
        assertEquals(CustomerServiceWorkbenchService.appEntryId("cs-1"), session.entryId)
        assertEquals("customer-1", session.visitorId)
        assertEquals("hello", session.lastMessagePreview)
    }

    @Test
    fun `unassigned customer private message is ignored`() {
        val state = WorkbenchState(
            users = linkedMapOf(
                "customer-1" to user("customer-1", "alice", "Alice", assignedCsUserId = "other-cs"),
                "cs-1" to user("cs-1", "cs01", "CS One", isOperator = true),
            ),
            accounts = linkedMapOf("account-1" to account("account-1", "cs-1")),
        )
        val service = state.service()

        val recorded = service.recordAssignedCustomerMessage(
            customerUserId = "customer-1",
            customerServiceUserId = "cs-1",
            contentType = 0,
            content = "hello",
        )

        assertNull(recorded)
        assertEquals(0, state.messages.size)
    }
}

private data class WorkbenchState(
    val users: MutableMap<String, User> = linkedMapOf(),
    val accounts: MutableMap<String, CustomerServiceAccount> = linkedMapOf(),
    val entries: MutableMap<String, WebCustomerServiceEntry> = linkedMapOf(),
    val sessions: MutableMap<String, WebCustomerServiceSession> = linkedMapOf(),
    val messages: MutableMap<String, WebCustomerServiceMessage> = linkedMapOf(),
) {
    fun service() = CustomerServiceWorkbenchService(
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
            uploadPort = unsupportedWorkbenchProxy(UploadPort::class.java),
            mongoTemplate = unusedMongoTemplate(),
        ),
    )

    private fun accountRepository(): CustomerServiceAccountRepository =
        proxy(CustomerServiceAccountRepository::class.java) { method, args ->
            when (method.name) {
                "findByUserId" -> accounts.values.firstOrNull { it.userId == args?.firstOrNull() }
                "save" -> {
                    val account = args?.firstOrNull() as CustomerServiceAccount
                    val saved = if (account.id == null) account.copy(id = "account-${accounts.size + 1}") else account
                    accounts[saved.id.orEmpty()] = saved
                    saved
                }
                else -> workbenchDefaultValue(method.returnType)
            }
        }

    private fun userRepository(): UserRepository =
        proxy(UserRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(users[args?.firstOrNull() as String])
                "findByIdAndIsDeletedFalse" -> users[args?.firstOrNull() as String]?.takeUnless { it.isDeleted }
                else -> workbenchDefaultValue(method.returnType)
            }
        }

    private fun entryRepository(): WebCustomerServiceEntryRepository =
        proxy(WebCustomerServiceEntryRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(entries[args?.firstOrNull() as String])
                "save" -> {
                    val entry = args?.firstOrNull() as WebCustomerServiceEntry
                    entries[entry.id.orEmpty()] = entry
                    entry
                }
                else -> workbenchDefaultValue(method.returnType)
            }
        }

    private fun sessionRepository(): WebCustomerServiceSessionRepository =
        proxy(WebCustomerServiceSessionRepository::class.java) { method, args ->
            when (method.name) {
                "findFirstByEntryIdAndVisitorIdAndStatusNotOrderByLastMessageAtDesc" -> {
                    val entryId = args?.getOrNull(0) as String
                    val visitorId = args.getOrNull(1) as String
                    val status = args.getOrNull(2) as WebCustomerServiceSessionStatus
                    sessions.values
                        .filter { it.entryId == entryId && it.visitorId == visitorId && it.status != status }
                        .maxByOrNull { it.lastMessageAt }
                }
                "save" -> {
                    val session = args?.firstOrNull() as WebCustomerServiceSession
                    val saved = if (session.id == null) session.copy(id = "session-${sessions.size + 1}") else session
                    sessions[saved.id.orEmpty()] = saved
                    saved
                }
                else -> workbenchDefaultValue(method.returnType)
            }
        }

    private fun messageRepository(): WebCustomerServiceMessageRepository =
        proxy(WebCustomerServiceMessageRepository::class.java) { method, args ->
            when (method.name) {
                "save" -> {
                    val message = args?.firstOrNull() as WebCustomerServiceMessage
                    val saved = if (message.id == null) message.copy(id = "message-${messages.size + 1}") else message
                    messages[saved.id.orEmpty()] = saved
                    saved
                }
                else -> workbenchDefaultValue(method.returnType)
            }
        }
}

private fun user(
    id: String,
    username: String,
    displayName: String,
    isOperator: Boolean = false,
    assignedCsUserId: String? = null,
) = User(
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
    assignedCsUserId = assignedCsUserId,
)

private fun account(id: String, userId: String) = CustomerServiceAccount(
    id = id,
    userId = userId,
    displayName = "Support",
    enabled = true,
)

private fun <T> proxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) } as T

private fun <T> unsupportedWorkbenchProxy(type: Class<T>): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> workbenchDefaultValue(method.returnType) } as T

private fun workbenchDefaultValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        java.util.Optional::class.java -> Optional.empty<Any>()
        List::class.java -> emptyList<Any>()
        else -> null
    }
