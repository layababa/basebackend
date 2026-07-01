package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "customer_service_external_api_credentials")
@CompoundIndexes(
    CompoundIndex(name = "cs_external_credentials_enabled_created_idx", def = "{ 'enabled': 1, 'createdAt': -1 }"),
)
data class CustomerServiceExternalApiCredential(
    @Id
    val id: String? = null,
    val name: String,
    @Indexed(unique = true)
    val apiKey: String,
    val apiSecret: String,
    @Indexed
    val qrCodeId: String,
    @Indexed
    val enabled: Boolean = true,
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
