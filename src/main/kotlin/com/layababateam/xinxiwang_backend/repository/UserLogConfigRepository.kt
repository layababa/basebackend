package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.UserLogConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserLogConfigRepository : MongoRepository<UserLogConfig, String> {
    fun findByUserId(userId: String): UserLogConfig?
}
