package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.FriendRequest
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface FriendRequestRepository : MongoRepository<FriendRequest, String> {
    fun findByToUserIdAndStatus(toUserId: String, status: Int): List<FriendRequest>
    fun findByFromUserIdAndToUserIdAndStatus(fromUserId: String, toUserId: String, status: Int): FriendRequest?
    fun findByFromUserIdOrToUserId(fromUserId: String, toUserId: String): List<FriendRequest>
    fun findByToUserIdOrderByCreatedAtDesc(toUserId: String): List<FriendRequest>
    fun findByToUserIdOrderByCreatedAtDesc(toUserId: String, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<FriendRequest>
}
