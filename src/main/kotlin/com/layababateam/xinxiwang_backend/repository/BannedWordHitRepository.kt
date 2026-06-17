package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BannedWordHit
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BannedWordHitRepository : MongoRepository<BannedWordHit, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<BannedWordHit>

    @Query("{ '\$or': [ { 'senderName': { '\$regex': ?0, '\$options': 'i' } }, { 'matchedWord': { '\$regex': ?0, '\$options': 'i' } }, { 'originalContent': { '\$regex': ?0, '\$options': 'i' } } ] }")
    fun searchByKeyword(keyword: String, pageable: Pageable): Page<BannedWordHit>
}
