package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CustomerServiceExternalApiCredentialRepository : MongoRepository<CustomerServiceExternalApiCredential, String> {
    fun findAllByOrderByCreatedAtDesc(): List<CustomerServiceExternalApiCredential>
    fun findByApiKey(apiKey: String): CustomerServiceExternalApiCredential?
}
