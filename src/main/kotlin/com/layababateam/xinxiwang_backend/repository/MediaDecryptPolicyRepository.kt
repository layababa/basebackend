package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicy
import com.layababateam.xinxiwang_backend.model.MediaDecryptPolicyScope
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MediaDecryptPolicyRepository : MongoRepository<MediaDecryptPolicy, String> {
    fun findByScope(scope: MediaDecryptPolicyScope): List<MediaDecryptPolicy>
    fun findByScopeAndUserId(scope: MediaDecryptPolicyScope, userId: String): List<MediaDecryptPolicy>
    fun findByScopeAndPlatform(scope: MediaDecryptPolicyScope, platform: String): List<MediaDecryptPolicy>
    fun findAllByOrderByUpdatedAtDesc(): List<MediaDecryptPolicy>
}
