package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.PushDaBinding
import org.springframework.data.mongodb.repository.MongoRepository

interface PushDaBindingRepository : MongoRepository<PushDaBinding, String> {
    fun findByUserId(userId: String): List<PushDaBinding>
    fun findByBindingUid(bindingUid: String): PushDaBinding?
    fun deleteByBindingUid(bindingUid: String)
    fun deleteByUserId(userId: String)
}
