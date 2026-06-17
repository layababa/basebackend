package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.ServerNode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ServerNodeRepository : MongoRepository<ServerNode, String> {
    fun findByEnabledTrueOrderBySortOrderAsc(): List<ServerNode>
}
