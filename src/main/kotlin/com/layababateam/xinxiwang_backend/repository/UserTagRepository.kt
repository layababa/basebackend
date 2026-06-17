package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.UserTag
import com.layababateam.xinxiwang_backend.model.UserTagBinding
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTagRepository : MongoRepository<UserTag, String> {
    fun existsByNormalizedName(normalizedName: String): Boolean
    fun findAllByOrderByCreatedAtDesc(): List<UserTag>
}

@Repository
interface UserTagBindingRepository : MongoRepository<UserTagBinding, String> {
    fun findByUserId(userId: String): List<UserTagBinding>
    fun findByUserIdOrderByCreatedAtAsc(userId: String): List<UserTagBinding>
    fun findByUserIdIn(userIds: List<String>): List<UserTagBinding>
    fun findByTagIdIn(tagIds: List<String>): List<UserTagBinding>
    fun deleteByUserId(userId: String)
}
