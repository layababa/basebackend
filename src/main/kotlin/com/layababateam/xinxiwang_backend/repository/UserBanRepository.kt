package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.UserBan
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserBanRepository : MongoRepository<UserBan, String> {
    fun findByUserIdAndIsActiveTrue(userId: String): UserBan?
    fun findByUserId(userId: String): List<UserBan>
    fun findByIsActiveTrueAndTypeAndExpiresAtLessThan(type: String, now: Long): List<UserBan>
}
