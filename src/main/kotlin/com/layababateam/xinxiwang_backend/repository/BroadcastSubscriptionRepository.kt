package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BroadcastSubscription
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Repository

@Repository
interface BroadcastSubscriptionRepository :
    MongoRepository<BroadcastSubscription, String> {

    fun findByBroadcastIdAndUserId(broadcastId: String, userId: String):
        BroadcastSubscription?

    fun findByBroadcastId(broadcastId: String): List<BroadcastSubscription>

    fun deleteByBroadcastIdAndUserId(broadcastId: String, userId: String): Long

    fun deleteByBroadcastId(broadcastId: String): Long

    /** reminder 已发 = true 的从待发候选里排除。 */
    fun findByBroadcastIdAndNotifiedReminderFalse(broadcastId: String):
        List<BroadcastSubscription>

    fun findByBroadcastIdAndNotifiedStartFalse(broadcastId: String):
        List<BroadcastSubscription>

    /** 调度 dispatch 之后批量标记已发，避免重复推。 */
    @Query("{ 'broadcastId': ?0 }")
    @Update("{ '\$set': { 'notifiedReminder': true } }")
    fun markAllReminderSent(broadcastId: String): Long

    @Query("{ 'broadcastId': ?0 }")
    @Update("{ '\$set': { 'notifiedStart': true } }")
    fun markAllStartSent(broadcastId: String): Long
}
