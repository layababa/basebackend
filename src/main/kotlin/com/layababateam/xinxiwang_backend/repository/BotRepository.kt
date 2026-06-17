package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.Bot
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BotRepository : MongoRepository<Bot, String> {
    fun findByUserId(userId: String): Bot?
    fun findByUsername(username: String): Bot?
    fun existsByUsername(username: String): Boolean
}
