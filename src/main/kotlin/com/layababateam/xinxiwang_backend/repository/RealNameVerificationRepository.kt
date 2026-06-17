package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.RealNameVerification
import com.layababateam.xinxiwang_backend.model.RealNameVerificationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface RealNameVerificationRepository : MongoRepository<RealNameVerification, String> {
    fun findByUserId(userId: String): RealNameVerification?
    fun findByIdCardNumber(idCardNumber: String): RealNameVerification?
    fun existsByIdCardNumberAndUserIdNot(idCardNumber: String, userId: String): Boolean
    fun findByStatusOrderByUpdatedAtDesc(status: RealNameVerificationStatus, pageable: Pageable): Page<RealNameVerification>
    fun findAllByOrderByUpdatedAtDesc(pageable: Pageable): Page<RealNameVerification>
    fun countByStatus(status: RealNameVerificationStatus): Long
}
