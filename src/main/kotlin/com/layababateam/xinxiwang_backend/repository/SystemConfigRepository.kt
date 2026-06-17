package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.SystemConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SystemConfigRepository : MongoRepository<SystemConfig, String> {
    fun findByKey(key: String): SystemConfig?
    fun findByKeyIn(keys: List<String>): List<SystemConfig>
}
