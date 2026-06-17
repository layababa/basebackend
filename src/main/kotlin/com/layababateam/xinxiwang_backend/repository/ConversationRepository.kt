package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Conversation
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ConversationRepository : MongoRepository<Conversation, String> {
    fun findByMembersContaining(userId: String): List<Conversation>
    fun findByMembersContaining(userId: String, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Conversation>
    fun findByTypeAndSearchableAndNameContainingIgnoreCase(type: Int, searchable: Boolean, name: String): List<Conversation>
    fun findByMembersContainingAndType(userId: String, type: Int): List<Conversation>
    fun findByMembersContainingAndType(userId: String, type: Int, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<Conversation>
    fun findByMembersContainingAndLastMessageTimeGreaterThanEqual(userId: String, afterTimestamp: Long): List<Conversation>

    @Query("{ 'type': ?1, 'members': { '\$all': ?0, '\$size': 2 } }")
    fun findPrivateChatByMembers(members: List<String>, type: Int): Conversation?

    fun countByType(type: Int): Long
}
