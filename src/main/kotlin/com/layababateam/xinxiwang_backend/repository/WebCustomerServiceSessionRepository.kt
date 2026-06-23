package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WebCustomerServiceSessionRepository : MongoRepository<WebCustomerServiceSession, String> {
    fun findFirstByEntryIdAndVisitorIdAndStatusNotOrderByLastMessageAtDesc(
        entryId: String,
        visitorId: String,
        status: WebCustomerServiceSessionStatus,
    ): WebCustomerServiceSession?
}
