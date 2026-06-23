package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.WebCustomerServiceMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WebCustomerServiceMessageRepository : MongoRepository<WebCustomerServiceMessage, String> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: String, pageable: Pageable): List<WebCustomerServiceMessage>
    fun findBySessionIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
        sessionId: String,
        createdAt: Long,
        pageable: Pageable,
    ): List<WebCustomerServiceMessage>
    fun findBySessionIdOrderByCreatedAtDesc(sessionId: String, pageable: Pageable): List<WebCustomerServiceMessage>
    fun findBySessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(
        sessionId: String,
        createdAt: Long,
        pageable: Pageable,
    ): List<WebCustomerServiceMessage>
}
