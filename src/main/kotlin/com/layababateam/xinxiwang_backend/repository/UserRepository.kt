package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.User
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : MongoRepository<User, String> {
    fun findByUsername(username: String): User?

    @Query("{ '_id': ?0, 'isDeleted': { '\$ne': true } }")
    fun findByIdAndIsDeletedFalse(id: String): User?

    @Query("{ 'username': ?0, 'isDeleted': { '\$ne': true } }")
    fun findByUsernameAndIsDeletedFalse(username: String): User?

    fun findByMyInviteCode(myInviteCode: String): User?
    fun findByDisplayNameContainingIgnoreCase(displayName: String): List<User>
    fun findByBscAddress(bscAddress: String): User?
    fun existsByUsername(username: String): Boolean
    fun countByInvitedBy(invitedBy: String): Long

    @Query("{ '\$text': { '\$search': ?0 } }")
    fun searchByDisplayName(keyword: String): List<User>
}
