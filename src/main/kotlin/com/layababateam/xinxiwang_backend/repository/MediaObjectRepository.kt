package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.MediaObject
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MediaObjectRepository : MongoRepository<MediaObject, String> {
    fun findFirstByMediaId(mediaId: String): MediaObject?

    fun existsByMediaId(mediaId: String): Boolean

    fun findAllByOwnerId(ownerId: String): List<MediaObject>
}
