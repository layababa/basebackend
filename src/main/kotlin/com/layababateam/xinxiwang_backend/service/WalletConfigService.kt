package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.layababateam.xinxiwang_backend.model.SystemConfig
import com.layababateam.xinxiwang_backend.model.WalletCurrencyConfig
import com.layababateam.xinxiwang_backend.repository.SystemConfigRepository
import com.layababateam.xinxiwang_backend.repository.WalletCurrencyConfigRepository
import com.layababateam.xinxiwang_backend.service.cache.SystemConfigCacheService
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class WalletConfigService(
    private val walletCurrencyConfigRepository: WalletCurrencyConfigRepository,
    private val systemConfigRepository: SystemConfigRepository,
    private val systemConfigCacheService: SystemConfigCacheService,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(WalletConfigService::class.java)

    companion object {
        private const val CACHE_KEY = "rentmsg:wallet:currencies-config"
        private val CACHE_TTL = Duration.ofMinutes(5)

        const val KEY_WALLET_ENABLE_DEPOSIT = "wallet.enableDeposit"
        const val KEY_WALLET_ENABLE_WITHDRAW = "wallet.enableWithdraw"
    }

    fun getEnabledCurrencies(): List<WalletCurrencyConfig> {
        val cached = try {
            redisTemplate.opsForValue().get(CACHE_KEY)
        } catch (e: Exception) {
            log.warn("Failed to read currencies cache from Redis", e)
            null
        }

        if (cached != null) {
            return try {
                objectMapper.readValue<List<WalletCurrencyConfig>>(cached)
            } catch (e: Exception) {
                log.warn("Failed to deserialize currencies cache, fallback to DB", e)
                redisTemplate.delete(CACHE_KEY)
                loadAndCacheEnabledCurrencies()
            }
        }

        return loadAndCacheEnabledCurrencies()
    }

    private fun loadAndCacheEnabledCurrencies(): List<WalletCurrencyConfig> {
        val currencies = walletCurrencyConfigRepository.findByEnabledTrueOrderBySortOrderAsc()
        try {
            val json = objectMapper.writeValueAsString(currencies)
            redisTemplate.opsForValue().set(CACHE_KEY, json, CACHE_TTL)
        } catch (e: Exception) {
            log.warn("Failed to cache currencies config", e)
        }
        return currencies
    }

    fun getAllCurrencies(): List<WalletCurrencyConfig> =
        walletCurrencyConfigRepository.findAll().sortedBy { it.sortOrder }

    fun createCurrency(
        currencyId: String,
        name: String,
        englishName: String,
        icon: String,
        type: String,
        depositEnabled: Boolean,
        withdrawEnabled: Boolean,
        sortOrder: Int,
    ): WalletCurrencyConfig {
        if (currencyId.isBlank()) {
            throw IllegalArgumentException("currencyId \u4e0d\u80fd\u4e3a\u7a7a")
        }
        if (walletCurrencyConfigRepository.existsByCurrencyId(currencyId)) {
            throw IllegalArgumentException("currencyId '$currencyId' \u5df2\u5b58\u5728")
        }

        val config = WalletCurrencyConfig(
            currencyId = currencyId,
            name = name,
            englishName = englishName,
            icon = icon,
            type = type,
            depositEnabled = depositEnabled,
            withdrawEnabled = withdrawEnabled,
            sortOrder = sortOrder,
        )

        val saved = walletCurrencyConfigRepository.save(config)
        invalidateCurrenciesCache()
        log.info("Created wallet currency config: {}", currencyId)
        return saved
    }

    fun updateCurrency(
        id: String,
        name: String?,
        englishName: String?,
        icon: String?,
        type: String?,
        depositEnabled: Boolean?,
        withdrawEnabled: Boolean?,
        sortOrder: Int?,
    ): WalletCurrencyConfig {
        val existing = walletCurrencyConfigRepository.findById(id).orElseThrow {
            IllegalArgumentException("\u5e01\u79cd\u914d\u7f6e\u4e0d\u5b58\u5728")
        }

        val updated = existing.copy(
            name = name ?: existing.name,
            englishName = englishName ?: existing.englishName,
            icon = icon ?: existing.icon,
            type = type ?: existing.type,
            depositEnabled = depositEnabled ?: existing.depositEnabled,
            withdrawEnabled = withdrawEnabled ?: existing.withdrawEnabled,
            sortOrder = sortOrder ?: existing.sortOrder,
            updatedAt = System.currentTimeMillis(),
        )

        val saved = walletCurrencyConfigRepository.save(updated)
        invalidateCurrenciesCache()
        log.info("Updated wallet currency config: {} (id={})", saved.currencyId, id)
        return saved
    }

    fun deleteCurrency(id: String): WalletCurrencyConfig {
        val existing = walletCurrencyConfigRepository.findById(id).orElseThrow {
            IllegalArgumentException("\u5e01\u79cd\u914d\u7f6e\u4e0d\u5b58\u5728")
        }

        val disabled = existing.copy(
            enabled = false,
            updatedAt = System.currentTimeMillis(),
        )

        val saved = walletCurrencyConfigRepository.save(disabled)
        invalidateCurrenciesCache()
        log.info("Soft-deleted wallet currency config: {} (id={})", saved.currencyId, id)
        return saved
    }

    fun getGlobalSwitches(): Map<String, Boolean> {
        val enableDeposit = systemConfigCacheService.getBooleanValue(KEY_WALLET_ENABLE_DEPOSIT, true)
        val enableWithdraw = systemConfigCacheService.getBooleanValue(KEY_WALLET_ENABLE_WITHDRAW, true)
        return mapOf(
            "enableDeposit" to enableDeposit,
            "enableWithdraw" to enableWithdraw,
        )
    }

    fun updateGlobalSwitches(enableDeposit: Boolean, enableWithdraw: Boolean) {
        val configMap = mapOf(
            KEY_WALLET_ENABLE_DEPOSIT to enableDeposit.toString(),
            KEY_WALLET_ENABLE_WITHDRAW to enableWithdraw.toString(),
        )

        val existingConfigs = systemConfigRepository.findByKeyIn(configMap.keys.toList())
            .associateBy { it.key }

        val toSave = configMap.map { (key, value) ->
            val existing = existingConfigs[key]
            if (existing != null) {
                existing.copy(value = value, updatedAt = System.currentTimeMillis())
            } else {
                SystemConfig(
                    key = key,
                    value = value,
                    description = when (key) {
                        KEY_WALLET_ENABLE_DEPOSIT -> "\u94b1\u5305\u5145\u503c\u5168\u5c40\u5f00\u5173"
                        KEY_WALLET_ENABLE_WITHDRAW -> "\u94b1\u5305\u63d0\u73b0\u5168\u5c40\u5f00\u5173"
                        else -> ""
                    },
                )
            }
        }

        systemConfigRepository.saveAll(toSave)
        systemConfigCacheService.invalidateAll(configMap.keys.toList())
        log.info("Updated wallet global switches: deposit={}, withdraw={}", enableDeposit, enableWithdraw)
    }

    fun getConfigForClient(): Map<String, Any> {
        val switches = getGlobalSwitches()
        val currencies = getEnabledCurrencies().map { c ->
            mapOf(
                "currencyId" to c.currencyId,
                "name" to c.name,
                "englishName" to c.englishName,
                "icon" to c.icon,
                "type" to c.type,
                "depositEnabled" to c.depositEnabled,
                "withdrawEnabled" to c.withdrawEnabled,
            )
        }

        return mapOf(
            "enableDeposit" to switches.getValue("enableDeposit"),
            "enableWithdraw" to switches.getValue("enableWithdraw"),
            "currencies" to currencies,
        )
    }

    private fun invalidateCurrenciesCache() {
        try {
            redisTemplate.delete(CACHE_KEY)
        } catch (e: Exception) {
            log.warn("Failed to invalidate currencies cache", e)
        }
    }
}
