package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BroadcastLuckyBag
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BroadcastLuckyBagRepository : MongoRepository<BroadcastLuckyBag, String> {
    fun findByBroadcastIdAndStatus(broadcastId: String, status: String): List<BroadcastLuckyBag>

    @Query("{ 'status': 'active', 'drawAt': { '\$lte': ?0 } }")
    fun findDue(now: Long): List<BroadcastLuckyBag>
}
