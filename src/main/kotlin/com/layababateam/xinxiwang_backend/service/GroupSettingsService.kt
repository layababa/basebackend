package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.config.RabbitMQConfig
import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.ConversationType
import com.layababateam.xinxiwang_backend.repository.ConversationRepository
import com.layababateam.xinxiwang_backend.service.cache.ConversationCacheService
import com.layababateam.xinxiwang_backend.service.cache.UserCacheService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 群組設定服務
 *
 * 負責群聊的各項設定操作：群資訊修改、公告、禁言、連結封鎖、
 * 加好友模式、入群方式、可搜尋性、歷史訊息可見性、最大成員數。
 */
@Service
class GroupSettingsService(
    private val conversationRepository: ConversationRepository,
    private val conversationCacheService: ConversationCacheService,
    private val rabbitTemplate: RabbitTemplate,
    private val rabbitPublishService: RabbitPublishService,
    private val userCacheService: UserCacheService,
    private val objectMapper: ObjectMapper,
    private val mongoTemplate: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(GroupSettingsService::class.java)

    companion object {
        /**
         * "仅群成员" 历史选项（= 3）已按产品规格废弃并隐藏。
         * 读/写两侧将 3 归一为 0 （所有人），DB 旧数据无需迁移即可生效。
         */
        const val LEGACY_MEMBER_ONLY: Int = 3

        fun normalizeAddFriendMode(raw: Int): Int =
            if (raw == LEGACY_MEMBER_ONLY) 0 else raw
    }

    fun updateGroupInfo(operatorId: String, convId: String, name: String?, avatarUrl: String?) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        requireAdmin(conv, operatorId)

        if (name != null) {
            require(name.length <= 30) { "群名称不能超过30个字符" }
        }

        val newName = name ?: conv.name
        val newAvatar = avatarUrl ?: conv.avatarUrl
        val fields = mutableMapOf<String, Any?>()
        if (name != null) fields["name"] = newName
        if (avatarUrl != null) fields["avatarUrl"] = newAvatar
        if (fields.isNotEmpty()) {
            updateConvFields(convId, fields)
        }

        publishGroupEvent("group_info_updated", conv.members, mapOf(
            "conversationId" to convId,
            "name" to newName,
            "avatarUrl" to newAvatar
        ))
    }

    /**
     * 解析 announcement 字段中的 JSON 数组，兼容旧版纯文本格式。
     */
    private fun parseAnnouncements(raw: String): MutableList<MutableMap<String, Any?>> {
        if (raw.isBlank()) return mutableListOf()
        return try {
            objectMapper.readValue<MutableList<MutableMap<String, Any?>>>(raw)
        } catch (_: Exception) {
            // 旧版纯文本格式：包装为单条公告
            mutableListOf(mutableMapOf(
                "id" to UUID.randomUUID().toString(),
                "content" to raw,
                "publisherId" to null,
                "publisherName" to null,
                "publishedAt" to 0L
            ))
        }
    }

    fun setAnnouncement(operatorId: String, convId: String, content: String) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        requireAdmin(conv, operatorId)

        val publisher = userCacheService.getUser(operatorId)
        val now = System.currentTimeMillis()

        val announcements = parseAnnouncements(conv.announcement)
        val newEntry = mapOf(
            "id" to UUID.randomUUID().toString(),
            "content" to content,
            "publisherId" to operatorId,
            "publisherName" to (publisher?.displayName ?: operatorId),
            "publisherAvatar" to (publisher?.avatarUrl ?: ""),
            "publishedAt" to now
        )
        announcements.add(0, newEntry.toMutableMap())

        val announcementJson = objectMapper.writeValueAsString(announcements)
        updateConvFields(convId, mapOf(
            "announcement" to announcementJson,
            "announcementUpdatedAt" to now,
            "announcementUpdatedBy" to operatorId
        ))

        publishGroupEvent("group_announcement_updated", conv.members, mapOf(
            "conversationId" to convId,
            "announcement" to announcementJson,
            "updatedAt" to now,
            "updatedBy" to operatorId
        ))
    }

    fun deleteAnnouncement(operatorId: String, convId: String, announcementId: String) {
        val conv = getConvOrThrow(convId)
        requireGroup(conv)
        requireAdmin(conv, operatorId)

        val announcements = parseAnnouncements(conv.announcement)
        val removed = announcements.removeIf { it["id"] == announcementId }
        if (!removed) return

        val now = System.currentTimeMillis()
        val announcementJson = objectMapper.writeValueAsString(announcements)
        updateConvFields(convId, mapOf(
            "announcement" to announcementJson,
            "announcementUpdatedAt" to now
        ))

        publishGroupEvent("group_announcement_updated", conv.members, mapOf(
            "conversationId" to convId,
            "announcement" to announcementJson,
            "updatedAt" to now,
            "updatedBy" to operatorId
        ))
    }

    fun setMuteAll(operatorId: String, convId: String, mute: Boolean) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        updateConvFields(convId, mapOf("muteAll" to mute))
        pushSettingsUpdate(convId, conv.members, mapOf("muteAll" to mute))
    }

    fun muteMember(operatorId: String, convId: String, targetId: String, mute: Boolean) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        require(targetId != conv.ownerId) { "不能禁言群主" }
        val updated = if (mute) (conv.mutedMembers + targetId).distinct() else conv.mutedMembers - targetId
        updateConvFields(convId, mapOf("mutedMembers" to updated))
        pushSettingsUpdate(convId, conv.members, mapOf("mutedMembers" to updated))
    }

    fun setBlockLinks(operatorId: String, convId: String, block: Boolean) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        updateConvFields(convId, mapOf("blockLinks" to block))
        pushSettingsUpdate(convId, conv.members, mapOf("blockLinks" to block))
    }

    fun setAddFriendMode(operatorId: String, convId: String, mode: Int) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        val normalized = normalizeAddFriendMode(mode)
        require(normalized in 0..2) { "无效的添加好友权限: $mode" }
        updateConvFields(convId, mapOf("addFriendMode" to normalized))
        pushSettingsUpdate(convId, conv.members, mapOf("addFriendMode" to normalized))
    }

    fun setJoinMode(operatorId: String, convId: String, mode: Int) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        updateConvFields(convId, mapOf("joinMode" to mode))
        pushSettingsUpdate(convId, conv.members, mapOf("joinMode" to mode))
    }

    fun setSearchable(operatorId: String, convId: String, value: Boolean) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        updateConvFields(convId, mapOf("searchable" to value))
        pushSettingsUpdate(convId, conv.members, mapOf("searchable" to value))
    }

    fun setHistoryVisible(operatorId: String, convId: String, value: Boolean) {
        val conv = getConvOrThrow(convId)
        requireAdmin(conv, operatorId)
        updateConvFields(convId, mapOf("historyVisible" to value))
        pushSettingsUpdate(convId, conv.members, mapOf("historyVisible" to value))
    }

    fun setMaxMembers(ownerId: String, convId: String, max: Int) {
        val conv = getConvOrThrow(convId)
        require(ownerId == conv.ownerId) { "仅群主可修改最大成员数" }
        updateConvFields(convId, mapOf("maxMembers" to max))
        pushSettingsUpdate(convId, conv.members, mapOf("maxMembers" to max))
    }

    // ── Private helpers ──

    /**
     * 用 MongoTemplate $set 部分更新会话字段，避免 save(conv.copy(...)) 那种
     * 基于陈旧缓存快照的全量回写把并发修改过的其他字段（例如 addFriendMode）
     * 静默刷回默认值。只触碰 fields 里明确指定的列，从根上消除写-写覆盖竞态。
     */
    private fun updateConvFields(convId: String, fields: Map<String, Any?>) {
        if (fields.isEmpty()) return
        val update = Update()
        fields.forEach { (k, v) -> update.set(k, v) }
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(convId)),
            update,
            Conversation::class.java
        )
        conversationCacheService.invalidate(convId)
    }

    private fun getConvOrThrow(convId: String): Conversation {
        return conversationCacheService.getConversation(convId)
            ?: throw IllegalArgumentException("会话不存在")
    }

    private fun requireGroup(conv: Conversation) {
        require(conv.type == ConversationType.GROUP.value) { "此操作仅适用于群聊" }
    }

    private fun requireAdmin(conv: Conversation, userId: String) {
        require(userId == conv.ownerId || userId in conv.adminIds) { "需要管理员权限" }
    }

    private fun publishGroupEvent(type: String, memberIds: List<String>, data: Map<String, Any?>) {
        rabbitPublishService.send(
            RabbitMQConfig.EVENT_GROUP_EXCHANGE,
            "",
            mapOf("type" to type, "memberIds" to memberIds, "data" to data),
            "group_settings_event:$type members=${memberIds.size} conv=${data["conversationId"] ?: "-"}",
        )
    }

    private fun pushSettingsUpdate(convId: String, members: List<String>, changes: Map<String, Any?>) {
        publishGroupEvent("group_settings_updated", members, changes + ("conversationId" to convId))
    }
}
