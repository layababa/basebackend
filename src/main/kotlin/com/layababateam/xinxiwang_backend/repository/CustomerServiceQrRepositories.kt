package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrBinding
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrCode
import com.layababateam.xinxiwang_backend.model.CustomerServiceQrReservation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CustomerServiceAccountRepository : MongoRepository<CustomerServiceAccount, String> {
    fun findAllByOrderBySortOrderAscCreatedAtAsc(): List<CustomerServiceAccount>
    fun findByUserId(userId: String): CustomerServiceAccount?
    fun findByUserIdInAndEnabledTrue(userIds: Collection<String>): List<CustomerServiceAccount>
}

@Repository
interface CustomerServiceQrCodeRepository : MongoRepository<CustomerServiceQrCode, String> {
    fun findAllByOrderByCreatedAtDesc(): List<CustomerServiceQrCode>
    fun findByCode(code: String): CustomerServiceQrCode?
}

@Repository
interface CustomerServiceQrBindingRepository : MongoRepository<CustomerServiceQrBinding, String> {
    fun findByQrCodeId(qrCodeId: String): List<CustomerServiceQrBinding>
    fun findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc(qrCodeId: String): List<CustomerServiceQrBinding>
    fun findByQrCodeIdAndCustomerServiceId(qrCodeId: String, customerServiceId: String): CustomerServiceQrBinding?
}

@Repository
interface CustomerServiceQrReservationRepository : MongoRepository<CustomerServiceQrReservation, String> {
    fun findByToken(token: String): CustomerServiceQrReservation?
}
