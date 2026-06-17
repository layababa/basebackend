package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.GroupJoinRequest
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupJoinRequestRepository : MongoRepository<GroupJoinRequest, String> {
    fun findByConversationIdAndStatus(conversationId: String, status: Int): List<GroupJoinRequest>
    fun findByApplicantIdAndConversationIdAndStatus(applicantId: String, conversationId: String, status: Int): GroupJoinRequest?
}
