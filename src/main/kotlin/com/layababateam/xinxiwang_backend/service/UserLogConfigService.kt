package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.UserLogConfig
import com.layababateam.xinxiwang_backend.repository.UserLogConfigRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class UserLogConfigService(
    private val repository: UserLogConfigRepository,
    private val mongoTemplate: MongoTemplate,
) {
    data class UpdateResult(val config: UserLogConfig, val updated: Boolean)
    data class AckResult(val config: UserLogConfig, val accepted: Boolean)

    fun getOrDefault(userId: String): UserLogConfig =
        repository.findByUserId(userId) ?: UserLogConfig(userId = userId)

    fun update(
        userId: String,
        criticalLogEnabled: Boolean,
        expectedRevision: Long?,
        updatedBy: String?,
    ): UpdateResult {
        val current = getOrDefault(userId)
        if (expectedRevision != null && expectedRevision != current.revision) {
            return UpdateResult(current, updated = false)
        }
        val now = System.currentTimeMillis()
        if (current.id == null) {
            val inserted = current.copy(
                criticalLogEnabled = criticalLogEnabled,
                revision = current.revision + 1,
                updatedAt = now,
                updatedBy = updatedBy,
                ackedDeviceIds = emptySet(),
            )
            return try {
                UpdateResult(repository.insert(inserted), updated = true)
            } catch (_: DuplicateKeyException) {
                UpdateResult(getOrDefault(userId), updated = false)
            }
        }

        val query = Query(
            Criteria.where("userId").`is`(userId)
                .and("revision").`is`(current.revision),
        )
        val update = Update()
            .set("criticalLogEnabled", criticalLogEnabled)
            .inc("revision", 1)
            .set("updatedAt", now)
            .set("updatedBy", updatedBy)
            .set("ackedDeviceIds", emptySet<String>())
        val updatedConfig = mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            UserLogConfig::class.java,
        )
        return if (updatedConfig != null) {
            UpdateResult(updatedConfig, updated = true)
        } else {
            UpdateResult(getOrDefault(userId), updated = false)
        }
    }

    fun ack(
        userId: String,
        deviceId: String?,
        revision: Long,
        criticalLogEnabled: Boolean,
    ): AckResult {
        val current = getOrDefault(userId)
        if (deviceId.isNullOrBlank() ||
            current.revision != revision ||
            current.criticalLogEnabled != criticalLogEnabled
        ) {
            return AckResult(current, accepted = false)
        }
        val query = Query(
            Criteria.where("userId").`is`(userId)
                .and("revision").`is`(revision)
                .and("criticalLogEnabled").`is`(criticalLogEnabled),
        )
        val update = Update().addToSet("ackedDeviceIds", deviceId)
        val updatedConfig = mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            UserLogConfig::class.java,
        )
        return if (updatedConfig != null) {
            AckResult(updatedConfig, accepted = true)
        } else {
            AckResult(getOrDefault(userId), accepted = false)
        }
    }
}
