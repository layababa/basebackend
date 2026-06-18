package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.LoginSecurityAlert
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface LoginSecurityAlertRepository : MongoRepository<LoginSecurityAlert, String> {
    fun countByStatus(status: String): Long
}
