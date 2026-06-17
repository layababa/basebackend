package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.BannedWord
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BannedWordRepository : MongoRepository<BannedWord, String> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<BannedWord>
    fun existsByWord(word: String): Boolean
}
