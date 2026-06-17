package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.WalletCurrencyConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WalletCurrencyConfigRepository : MongoRepository<WalletCurrencyConfig, String> {
    fun findByEnabledTrueOrderBySortOrderAsc(): List<WalletCurrencyConfig>
    fun findByCurrencyId(currencyId: String): WalletCurrencyConfig?
    fun existsByCurrencyId(currencyId: String): Boolean
}
