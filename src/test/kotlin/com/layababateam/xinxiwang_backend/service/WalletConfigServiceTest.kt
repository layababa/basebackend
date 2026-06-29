package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.layababateam.xinxiwang_backend.controller.AdminWalletConfigController
import com.layababateam.xinxiwang_backend.controller.CreateCurrencyRequest
import com.layababateam.xinxiwang_backend.model.SystemConfig
import com.layababateam.xinxiwang_backend.model.WalletCurrencyConfig
import com.layababateam.xinxiwang_backend.repository.SystemConfigRepository
import com.layababateam.xinxiwang_backend.repository.WalletCurrencyConfigRepository
import com.layababateam.xinxiwang_backend.service.cache.SystemConfigCacheService
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class WalletConfigServiceTest {
    @Test
    fun `client config combines global switches and enabled currencies`() {
        val redisTemplate = RecordingRedisTemplate()
        val systemConfigRepository = systemConfigRepository(
            mapOf(
                WalletConfigService.KEY_WALLET_ENABLE_DEPOSIT to SystemConfig(
                    key = WalletConfigService.KEY_WALLET_ENABLE_DEPOSIT,
                    value = "false",
                ),
                WalletConfigService.KEY_WALLET_ENABLE_WITHDRAW to SystemConfig(
                    key = WalletConfigService.KEY_WALLET_ENABLE_WITHDRAW,
                    value = "true",
                ),
            ),
        )
        val service = WalletConfigService(
            walletCurrencyConfigRepository = walletCurrencyConfigRepository(
                listOf(
                    WalletCurrencyConfig(
                        id = "id-1",
                        currencyId = "points",
                        name = "Points",
                        englishName = "Points",
                        icon = "points.png",
                        type = "points",
                        depositEnabled = true,
                        withdrawEnabled = false,
                        sortOrder = 1,
                    ),
                ),
            ),
            systemConfigRepository = systemConfigRepository,
            systemConfigCacheService = SystemConfigCacheService(redisTemplate, systemConfigRepository),
            redisTemplate = redisTemplate,
            objectMapper = jacksonObjectMapper(),
        )

        val config = service.getConfigForClient()

        assertEquals(false, config["enableDeposit"])
        assertEquals(true, config["enableWithdraw"])
        val currencies = config["currencies"] as List<*>
        val currency = currencies.single() as Map<*, *>
        assertEquals("points", currency["currencyId"])
        assertEquals("Points", currency["name"])
        assertEquals(true, currency["depositEnabled"])
        assertEquals(false, currency["withdrawEnabled"])
    }

    @Test
    fun `admin wallet config rejects blank currency id before touching service`() {
        val controller = AdminWalletConfigController(uninitialized(WalletConfigService::class.java))

        val response = controller.createCurrency(
            CreateCurrencyRequest(
                currencyId = "",
                name = "Points",
                englishName = "Points",
                icon = "points.png",
                type = "points",
            ),
        )

        assertEquals(400, response.statusCode.value())
        assertFalse(response.body!!.success)
        assertEquals("currencyId \u4e0d\u80fd\u4e3a\u7a7a", response.body!!.message)
    }

    private class RecordingRedisTemplate : StringRedisTemplate() {
        val values = linkedMapOf<String, String>()
        val valueTtls = linkedMapOf<String, Duration>()

        @Suppress("UNCHECKED_CAST")
        private val valueOperations = Proxy.newProxyInstance(
            ValueOperations::class.java.classLoader,
            arrayOf(ValueOperations::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "get" -> values[args?.get(0) as String]
                    "set" -> {
                        val key = args?.get(0) as String
                        values[key] = args[1] as String
                        val ttl = args.getOrNull(2)
                        if (ttl is Duration) valueTtls[key] = ttl
                        null
                    }
                    "toString" -> "RecordingValueOperations"
                    else -> error("Unsupported ValueOperations method: ${method.name}")
                }
            },
        ) as ValueOperations<String, String>

        override fun opsForValue(): ValueOperations<String, String> = valueOperations

        override fun delete(key: String): Boolean = values.remove(key) != null
    }

    private fun walletCurrencyConfigRepository(currencies: List<WalletCurrencyConfig>): WalletCurrencyConfigRepository =
        repositoryProxy(WalletCurrencyConfigRepository::class.java) { method, args ->
            when (method) {
                "findByEnabledTrueOrderBySortOrderAsc" -> currencies.filter { it.enabled }.sortedBy { it.sortOrder }
                "findAll" -> currencies
                "existsByCurrencyId" -> currencies.any { it.currencyId == args[0] as String }
                "findById" -> Optional.ofNullable(currencies.firstOrNull { it.id == args[0] as String })
                "save" -> args[0]
                else -> unsupportedRepositoryMethod("WalletCurrencyConfigRepository", method)
            }
        }

    private fun systemConfigRepository(configs: Map<String, SystemConfig>): SystemConfigRepository =
        repositoryProxy(SystemConfigRepository::class.java) { method, args ->
            when (method) {
                "findByKey" -> configs[args[0] as String]
                "findByKeyIn" -> (args[0] as List<*>).mapNotNull { configs[it as String] }
                "saveAll" -> args[0]
                else -> unsupportedRepositoryMethod("SystemConfigRepository", method)
            }
        }

    private fun <T> repositoryProxy(type: Class<T>, handler: (String, Array<Any?>) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(
                type.classLoader,
                arrayOf(type),
                InvocationHandler { _, method, args ->
                    when (method.name) {
                        "toString" -> "repository-proxy:${type.simpleName}"
                        "hashCode" -> System.identityHashCode(handler)
                        "equals" -> false
                        else -> handler(method.name, args ?: emptyArray())
                    }
                },
            ),
        )

    private fun unsupportedRepositoryMethod(type: String, method: String): Nothing =
        error("$type.$method should not be called by this test")

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(type: Class<T>): T {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(type) as T
    }
}
