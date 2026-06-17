package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BusinessCard
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessCardRepository : MongoRepository<BusinessCard, String> {
    fun findByUserId(userId: String): List<BusinessCard>
    fun findByUserIdAndIsDefaultTrue(userId: String): BusinessCard?
}
