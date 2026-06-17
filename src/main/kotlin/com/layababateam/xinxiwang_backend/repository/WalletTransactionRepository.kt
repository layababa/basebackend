package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.WalletTransaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WalletTransactionRepository : MongoRepository<WalletTransaction, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<WalletTransaction>
    fun findByUserIdAndTypeInOrderByCreatedAtDesc(userId: String, types: List<Int>, pageable: Pageable): Page<WalletTransaction>
    fun findByTxHash(txHash: String): WalletTransaction?
    fun existsByUserIdAndRedPacketIdAndType(userId: String, redPacketId: String, type: Int): Boolean
}
