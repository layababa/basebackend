package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.WithdrawRecord
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WithdrawRecordRepository : MongoRepository<WithdrawRecord, String> {
    fun findByStatus(status: Int, pageable: Pageable): Page<WithdrawRecord>
    fun findByUserId(userId: String, pageable: Pageable): Page<WithdrawRecord>
    fun findByUserIdAndStatus(userId: String, status: Int, pageable: Pageable): Page<WithdrawRecord>
    fun findByStatusIn(statuses: List<Int>, pageable: Pageable): Page<WithdrawRecord>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<WithdrawRecord>
}
