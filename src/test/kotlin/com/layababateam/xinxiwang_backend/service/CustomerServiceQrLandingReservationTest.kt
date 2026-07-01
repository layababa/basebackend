package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.controller.unusedMongoTemplate
import com.layababateam.xinxiwang_backend.model.AppLatestVersion
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrBinding
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrCode
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrReservation
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.AppLatestVersionRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrReservationRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomerServiceQrLandingReservationTest {
    @Test
    fun `landing reservation returns configured text assigned customer and app fallback urls`() {
        val state = QrLandingState(
            users = linkedMapOf("cs-1" to qrLandingUser("cs-1", "cs01", "Support")),
            accounts = linkedMapOf("account-1" to CustomerServiceAccount(id = "account-1", userId = "cs-1", displayName = "Support")),
            qrs = linkedMapOf(
                "qr-1" to CustomerServiceQrCode(
                    id = "qr-1",
                    name = "Mobile QR",
                    code = "mobile-code",
                    landingGuideText = "Scan to add support",
                    landingButtonText = "Add support",
                    landingFallbackText = "Install the app first",
                ),
            ),
            bindings = linkedMapOf(
                "binding-1" to CustomerServiceQrBinding(
                    id = "binding-1",
                    qrCodeId = "qr-1",
                    customerServiceId = "account-1",
                    enabled = true,
                ),
            ),
            appVersions = linkedMapOf(
                "android" to AppLatestVersion(platform = "android", latestVersion = "1.0.0", buildNumber = 1, downloadUrl = "https://download.example/app.apk"),
                "ios" to AppLatestVersion(platform = "ios", latestVersion = "1.0.0", buildNumber = 1, downloadUrl = "https://apps.example/app"),
            ),
        )
        val service = state.service()

        val response = service.createLandingReservation(
            code = "mobile-code",
            platform = "android",
            request = request("https", "admin.example.com"),
        )

        assertEquals("Scan to add support", response.guideText)
        assertEquals("Add support", response.buttonText)
        assertEquals("Install the app first", response.fallbackText)
        assertEquals("cs-1", response.customerService.userId)
        assertEquals("https://download.example/app.apk", response.downloadUrls["android"])
        assertEquals("https://apps.example/app", response.downloadUrls["ios"])
        assertTrue(response.appDeepLink.startsWith("xianyun://customerservice?="))
        assertEquals(response.reservationToken, state.reservations.values.single().token)
    }
}

private data class QrLandingState(
    val users: MutableMap<String, User> = linkedMapOf(),
    val accounts: MutableMap<String, CustomerServiceAccount> = linkedMapOf(),
    val qrs: MutableMap<String, CustomerServiceQrCode> = linkedMapOf(),
    val bindings: MutableMap<String, CustomerServiceQrBinding> = linkedMapOf(),
    val reservations: MutableMap<String, CustomerServiceQrReservation> = linkedMapOf(),
    val appVersions: MutableMap<String, AppLatestVersion> = linkedMapOf(),
) {
    fun service() = CustomerServiceQrAssignmentService(
        accountRepository = accountRepository(),
        qrCodeRepository = qrRepository(),
        bindingRepository = bindingRepository(),
        reservationRepository = reservationRepository(),
        appLatestVersionRepository = appVersionRepository(),
        userRepository = userRepository(),
        mongoTemplate = unusedMongoTemplate(),
        friendPortProvider = qrLandingFriendProvider(),
        configuredPublicBaseUrl = "",
        configuredAdminRouteBaseUrl = "",
    )

    private fun accountRepository(): CustomerServiceAccountRepository =
        qrLandingProxy(CustomerServiceAccountRepository::class.java) { method, args ->
            when (method.name) {
                "findByUserId" -> accounts.values.firstOrNull { it.userId == args?.firstOrNull() }
                "findAllById" -> {
                    val ids = (args?.firstOrNull() as Iterable<*>).map { it.toString() }.toSet()
                    accounts.values.filter { it.id in ids }
                }
                else -> qrLandingDefaultValue(method.returnType)
            }
        }

    private fun qrRepository(): CustomerServiceQrCodeRepository =
        qrLandingProxy(CustomerServiceQrCodeRepository::class.java) { method, args ->
            when (method.name) {
                "findByCode" -> qrs.values.firstOrNull { it.code == args?.firstOrNull() }
                else -> qrLandingDefaultValue(method.returnType)
            }
        }

    private fun bindingRepository(): CustomerServiceQrBindingRepository =
        qrLandingProxy(CustomerServiceQrBindingRepository::class.java) { method, args ->
            when (method.name) {
                "findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc" ->
                    bindings.values.filter { it.qrCodeId == args?.firstOrNull() as String }
                else -> qrLandingDefaultValue(method.returnType)
            }
        }

    private fun reservationRepository(): CustomerServiceQrReservationRepository =
        qrLandingProxy(CustomerServiceQrReservationRepository::class.java) { method, args ->
            when (method.name) {
                "save" -> {
                    val reservation = args?.firstOrNull() as CustomerServiceQrReservation
                    val saved = if (reservation.id == null) reservation.copy(id = "reservation-${reservations.size + 1}") else reservation
                    reservations[saved.id.orEmpty()] = saved
                    saved
                }
                else -> qrLandingDefaultValue(method.returnType)
            }
        }

    private fun appVersionRepository(): AppLatestVersionRepository =
        qrLandingProxy(AppLatestVersionRepository::class.java) { method, args ->
            when (method.name) {
                "findByPlatform" -> appVersions[args?.firstOrNull() as String]
                else -> qrLandingDefaultValue(method.returnType)
            }
        }

    private fun userRepository(): UserRepository =
        qrLandingProxy(UserRepository::class.java) { method, args ->
            when (method.name) {
                "findAllById" -> {
                    val ids = (args?.firstOrNull() as Iterable<*>).map { it.toString() }.toSet()
                    users.values.filter { it.id in ids }
                }
                else -> qrLandingDefaultValue(method.returnType)
            }
        }
}

private fun qrLandingUser(id: String, username: String, displayName: String) = User(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = "",
    gender = 0,
    bio = "Bio",
    passwordHash = "hash",
    inviteCode = "",
    myInviteCode = "invite-$id",
    isOperator = true,
)

private fun request(scheme: String, host: String): HttpServletRequest =
    qrLandingProxy(HttpServletRequest::class.java) { method, _ ->
        when (method.name) {
            "getHeader" -> null
            "getScheme" -> scheme
            "getServerName" -> host
            "getServerPort" -> 443
            else -> qrLandingDefaultValue(method.returnType)
        }
    }

@Suppress("UNCHECKED_CAST")
private fun qrLandingFriendProvider(): ObjectProvider<CustomerServiceFriendPort> =
    qrLandingProxy(ObjectProvider::class.java) { method, _ ->
        when (method.name) {
            "getIfAvailable", "getIfUnique" -> null
            "iterator" -> emptyList<CustomerServiceFriendPort>().iterator()
            "stream", "orderedStream" -> Stream.empty<CustomerServiceFriendPort>()
            else -> qrLandingDefaultValue(method.returnType)
        }
    } as ObjectProvider<CustomerServiceFriendPort>

private fun <T> qrLandingProxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) } as T

private fun qrLandingDefaultValue(type: Class<*>): Any? =
    when (type) {
        Boolean::class.javaPrimitiveType, java.lang.Boolean.TYPE -> false
        Int::class.javaPrimitiveType, java.lang.Integer.TYPE -> 0
        Long::class.javaPrimitiveType, java.lang.Long.TYPE -> 0L
        String::class.java -> ""
        Optional::class.java -> Optional.empty<Any>()
        List::class.java -> emptyList<Any>()
        Iterable::class.java -> emptyList<Any>()
        Stream::class.java -> Stream.empty<Any>()
        else -> null
    }
