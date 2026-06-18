package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.GroupRelay
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupRelayRepository : MongoRepository<GroupRelay, String> {
    fun findByConversationIdOrderByCreatedAtDesc(conversationId: String): List<GroupRelay>
}
