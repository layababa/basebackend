package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.LoginSecurityBlock
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface LoginSecurityBlockRepository : MongoRepository<LoginSecurityBlock, String> {
    fun findByActiveTrue(): List<LoginSecurityBlock>
    fun findByTypeAndValueAndActiveTrue(type: String, value: String): List<LoginSecurityBlock>
}
