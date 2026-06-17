package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

enum class RealNameVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED
}

@Document(collection = "real_name_verifications")
data class RealNameVerification(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val userId: String,
    val username: String,
    val displayName: String,
    val phone: String? = null,
    val realName: String,
    @Indexed(unique = true)
    val idCardNumber: String,
    val idCardFrontUrl: String,
    val idCardBackUrl: String,
    val handheldIdCardUrl: String? = null,
    @Indexed
    val status: RealNameVerificationStatus = RealNameVerificationStatus.PENDING,
    val rejectReason: String? = null,
    val reviewedBy: String? = null,
    val reviewedAdminId: String? = null,
    val reviewedAt: Long? = null,
    val submittedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
