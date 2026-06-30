package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.controller.unusedMongoTemplate
import com.layababateam.xinxiwang_backend.dto.CustomerServiceAccountRequest
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomerServiceQrAssignmentServiceAccountTest {
    @Test
    fun `customer service account can be created for a non operator user`() {
        val state = CustomerServiceAccountState(
            users = linkedMapOf(
                "user-1" to customerServiceTestUser("user-1", isOperator = false),
            ),
        )
        val service = state.service()

        val response = service.createAccount(
            CustomerServiceAccountRequest(
                userId = "user-1",
                displayName = "Support",
                enabled = true,
            ),
        )

        assertEquals("user-1", response.userId)
        assertEquals("Support", response.displayName)
        assertEquals("user-1", state.accounts.values.single().userId)
    }

    @Test
    fun `admin customer service service exposes user map status helper`() {
        assertTrue(
            AdminUserCustomerServiceService::class.java.methods.any { it.name == "userMapWithCustomerServiceStatus" },
        )
    }
}

private data class CustomerServiceAccountState(
    val users: MutableMap<String, User> = linkedMapOf(),
    val accounts: MutableMap<String, CustomerServiceAccount> = linkedMapOf(),
) {
    fun service() = CustomerServiceQrAssignmentService(
        accountRepository = accountRepository(),
        qrCodeRepository = unsupportedRepository(CustomerServiceQrCodeRepository::class.java),
        bindingRepository = unsupportedRepository(CustomerServiceQrBindingRepository::class.java),
        userRepository = userRepository(),
        mongoTemplate = unusedMongoTemplate(),
        friendPortProvider = unsupportedFriendProvider(),
        configuredPublicBaseUrl = "",
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
                else -> customerServiceDefaultValue(method.returnType)
            }
        }

    private fun userRepository(): UserRepository =
        proxy(UserRepository::class.java) { method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(users[args?.firstOrNull() as String])
                "findByIdAndIsDeletedFalse" -> users[args?.firstOrNull() as String]?.takeUnless { it.isDeleted }
                else -> customerServiceDefaultValue(method.returnType)
            }
        }
}

private fun customerServiceTestUser(id: String, isOperator: Boolean) = User(
    id = id,
    username = id,
    displayName = id,
    avatarUrl = "",
    gender = 0,
    bio = "",
    passwordHash = "hash",
    inviteCode = "",
    myInviteCode = "invite-$id",
    isOperator = isOperator,
)

private fun <T> unsupportedRepository(type: Class<T>): T =
    proxy(type) { method, _ ->
        customerServiceDefaultValue(method.returnType)
    }

@Suppress("UNCHECKED_CAST")
private fun unsupportedFriendProvider(): ObjectProvider<CustomerServiceFriendPort> =
    proxy(ObjectProvider::class.java) { method, _ ->
        when (method.name) {
            "getIfAvailable", "getIfUnique" -> null
            "iterator" -> emptyList<CustomerServiceFriendPort>().iterator()
            "stream", "orderedStream" -> Stream.empty<CustomerServiceFriendPort>()
            else -> customerServiceDefaultValue(method.returnType)
        }
    } as ObjectProvider<CustomerServiceFriendPort>

@Suppress("UNCHECKED_CAST")
private fun <T> proxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
        handler(method, args)
    } as T

private fun customerServiceDefaultValue(type: Class<*>): Any? =
    when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Void.TYPE -> null
        type == Optional::class.java -> Optional.empty<Any>()
        type == List::class.java -> emptyList<Any>()
        type == Iterable::class.java -> emptyList<Any>()
        type == Stream::class.java -> Stream.empty<Any>()
        else -> null
    }
