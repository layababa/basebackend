package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.AppLatestVersion
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AppLatestVersionRepository : MongoRepository<AppLatestVersion, String> {
    fun findByPlatform(platform: String): AppLatestVersion?
}
