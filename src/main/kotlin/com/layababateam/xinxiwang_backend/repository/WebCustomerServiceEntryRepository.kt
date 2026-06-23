package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.WebCustomerServiceEntry
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WebCustomerServiceEntryRepository : MongoRepository<WebCustomerServiceEntry, String> {
    fun findAllByOrderByCreatedAtDesc(): List<WebCustomerServiceEntry>
}
