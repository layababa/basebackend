package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "customer_service_accounts")
@CompoundIndexes(
    CompoundIndex(name = "cs_accounts_enabled_sort_created_idx", def = "{ 'enabled': 1, 'sortOrder': 1, 'createdAt': 1 }"),
)
data class CustomerServiceAccount(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val userId: String,
    val displayName: String? = null,
    val remark: String? = null,
    val sortOrder: Int = 0,
    @Indexed
    val enabled: Boolean = true,
    val assignedCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Document(collection = "customer_service_qr_codes")
@CompoundIndexes(
    CompoundIndex(name = "cs_qrs_enabled_created_idx", def = "{ 'enabled': 1, 'createdAt': -1 }"),
)
data class CustomerServiceQrCode(
    @Id
    val id: String? = null,
    val name: String,
    @Indexed(unique = true)
    val code: String,
    val remark: String? = null,
    val landingGuideText: String = "Tap the button to add support in the app.",
    val landingButtonText: String = "Add support",
    val landingFallbackText: String = "Install the app first if it does not open.",
    @Indexed
    val enabled: Boolean = true,
    val assignedCount: Long = 0,
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Document(collection = "customer_service_qr_reservations")
@CompoundIndexes(
    CompoundIndex(name = "cs_qr_reservations_expire_idx", def = "{ 'expiresAt': 1 }"),
)
data class CustomerServiceQrReservation(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val token: String,
    @Indexed
    val code: String,
    @Indexed
    val qrCodeId: String,
    val customerServiceId: String,
    @Indexed
    val customerServiceUserId: String,
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Document(collection = "customer_service_qr_bindings")
@CompoundIndexes(
    CompoundIndex(name = "cs_qr_bindings_qr_sort_idx", def = "{ 'qrCodeId': 1, 'enabled': 1, 'assignedCount': 1, 'sortOrder': 1, 'createdAt': 1 }"),
    CompoundIndex(name = "cs_qr_bindings_qr_account_unique_idx", def = "{ 'qrCodeId': 1, 'customerServiceId': 1 }", unique = true),
)
data class CustomerServiceQrBinding(
    @Id
    val id: String? = null,
    @Indexed
    val qrCodeId: String,
    @Indexed
    val customerServiceId: String,
    @Indexed
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val assignedCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
