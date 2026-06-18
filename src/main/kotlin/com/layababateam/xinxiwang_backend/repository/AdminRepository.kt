package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Admin
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface AdminRepository : MongoRepository<Admin, String> {
    fun findByUsername(username: String): Admin?
    fun findByIsActiveTrue(): List<Admin>
    fun existsByUsername(username: String): Boolean
}
