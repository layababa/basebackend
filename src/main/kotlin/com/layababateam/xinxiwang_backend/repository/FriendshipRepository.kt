package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Friendship
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface FriendshipRepository : MongoRepository<Friendship, String> {
    fun findByUserId(userId: String): List<Friendship>
    fun findByUserIdAndFriendId(userId: String, friendId: String): Friendship?
    fun findByFriendIdAndUserIdIn(friendId: String, userIds: List<String>): List<Friendship>
    fun deleteByUserIdAndFriendId(userId: String, friendId: String)
    fun deleteByUserId(userId: String)
    fun deleteByFriendId(friendId: String)

    fun findByUserIdAndVersionGreaterThanOrderByVersionAsc(
        userId: String, version: Long, pageable: Pageable
    ): List<Friendship>

    fun findTopByUserIdOrderByVersionDesc(userId: String): Friendship?
}
