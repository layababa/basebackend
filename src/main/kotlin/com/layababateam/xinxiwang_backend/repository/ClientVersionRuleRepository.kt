package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.ClientVersionRule
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ClientVersionRuleRepository : MongoRepository<ClientVersionRule, String> {
    fun findByPlatform(platform: String): ClientVersionRule?
    fun findByEnabledTrue(): List<ClientVersionRule>
    fun findByPlatformAndEnabledTrue(platform: String): ClientVersionRule?
}
