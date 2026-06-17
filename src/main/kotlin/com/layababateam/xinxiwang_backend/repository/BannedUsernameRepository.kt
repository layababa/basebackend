package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BannedUsername
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BannedUsernameRepository : MongoRepository<BannedUsername, String> {
    fun existsByUsername(username: String): Boolean
}
