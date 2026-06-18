package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.CheckinGroupConfig
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface CheckinGroupConfigRepository : MongoRepository<CheckinGroupConfig, String> {
    fun findFirstByConversationId(conversationId: String): CheckinGroupConfig?
}
