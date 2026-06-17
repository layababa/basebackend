package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Feedback
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface FeedbackRepository : MongoRepository<Feedback, String> {
    fun findByStatus(status: String): List<Feedback>
    fun findByStatus(status: String, pageable: Pageable): Page<Feedback>
    fun findByUserId(userId: String): List<Feedback>
    fun findByStatusNotIn(statuses: List<String>): List<Feedback>
    fun findByStatusNotIn(statuses: List<String>, pageable: Pageable): Page<Feedback>
}
