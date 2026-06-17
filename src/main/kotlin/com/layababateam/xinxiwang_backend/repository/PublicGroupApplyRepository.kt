package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.PublicGroupApply
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface PublicGroupApplyRepository : MongoRepository<PublicGroupApply, String> {
    fun findByStatus(status: Int, pageable: Pageable): Page<PublicGroupApply>
    fun findByGroupId(groupId: String): List<PublicGroupApply>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<PublicGroupApply>
}
