package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.GroupChain
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupChainRepository : MongoRepository<GroupChain, String> {
    fun findByMessageId(messageId: String): GroupChain?
    fun findByConversationIdAndStatus(conversationId: String, status: Int): List<GroupChain>
}
