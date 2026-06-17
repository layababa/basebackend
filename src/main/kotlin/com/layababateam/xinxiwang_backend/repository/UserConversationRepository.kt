package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.UserConversation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserConversationRepository : MongoRepository<UserConversation, String> {
    fun findByUserId(userId: String): List<UserConversation>
    fun findByUserId(userId: String, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<UserConversation>
    fun findFirstByUserIdAndConversationId(userId: String, conversationId: String): UserConversation?
    fun findByConversationIdIn(conversationIds: List<String>): List<UserConversation>
    fun findByConversationIdInAndUserIdIn(conversationIds: List<String>, userIds: List<String>): List<UserConversation>
    fun findByConversationId(conversationId: String): List<UserConversation>
    fun findByConversationId(conversationId: String, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<UserConversation>
    fun findByUserIdAndSavedToContacts(userId: String, savedToContacts: Boolean): List<UserConversation>
    fun deleteByUserIdAndConversationId(userId: String, conversationId: String)
    fun deleteAllByUserIdInAndConversationId(userIds: List<String>, conversationId: String)
    fun deleteByConversationId(conversationId: String)
    fun deleteByUserId(userId: String)
}
