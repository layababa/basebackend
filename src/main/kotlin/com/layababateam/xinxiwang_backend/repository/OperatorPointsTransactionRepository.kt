package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.OperatorPointsTransaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface OperatorPointsTransactionRepository :
    MongoRepository<OperatorPointsTransaction, String> {

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable):
        Page<OperatorPointsTransaction>

    fun findByTypeOrderByCreatedAtDesc(type: String, pageable: Pageable):
        Page<OperatorPointsTransaction>

    fun findByCreatedAtBetween(start: Long, end: Long): List<OperatorPointsTransaction>

    /** 幂等判重：按业务幂等键查找已有流水。 */
    fun findFirstByRefId(refId: String): OperatorPointsTransaction?

    /** 按操作者（用户）分页查流水。 */
    fun findByOperatorIdOrderByCreatedAtDesc(operatorId: String, pageable: Pageable):
        Page<OperatorPointsTransaction>

    /** 按操作者 + 业务原因分页查流水。 */
    fun findByOperatorIdAndReasonOrderByCreatedAtDesc(operatorId: String, reason: String, pageable: Pageable):
        Page<OperatorPointsTransaction>

    /** 按业务原因分页查流水。 */
    fun findByReasonOrderByCreatedAtDesc(reason: String, pageable: Pageable):
        Page<OperatorPointsTransaction>
}
