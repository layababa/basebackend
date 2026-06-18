package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.LoginSecurityEvent
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface LoginSecurityEventRepository : MongoRepository<LoginSecurityEvent, String> {
    fun deleteByCreatedAtBefore(createdAt: Long): Long
}
