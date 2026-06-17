package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "wallet_currency_configs")
data class WalletCurrencyConfig(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val currencyId: String,
    val name: String,
    val englishName: String,
    val icon: String,
    val type: String,           // "points" | "usdt" | custom
    val depositEnabled: Boolean = false,
    val withdrawEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
