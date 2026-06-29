package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.layababateam.xinxiwang_backend.dto.MeetingChatMessageDto
import com.layababateam.xinxiwang_backend.dto.MeetingDto
import com.layababateam.xinxiwang_backend.dto.MeetingJoinResponse
import com.layababateam.xinxiwang_backend.dto.ParticipantDto
import com.layababateam.xinxiwang_backend.extensions.escapeRegex
import com.layababateam.xinxiwang_backend.model.Meeting
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.repository.MeetingChatMessageRepository
import com.layababateam.xinxiwang_backend.repository.MeetingRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * 会议密码校验异常：需要密码但未提供。
 */
class MeetingPasswordRequiredException(message: String = "该会议需要密码") :
    RuntimeException(message)

/**
 * 会议密码校验异常：密码错误。
 */
class MeetingPasswordIncorrectException(message: String = "会议密码错误") :
    RuntimeException(message)

@Service
class MeetingService(
    private val meetingRepository: MeetingRepository,
    private val meetingChatMessageRepository: MeetingChatMessageRepository,
    private val meetingTrtcService: MeetingTrtcService,
    private val userRepository: UserRepository,
    private val userSessionManager: UserSessionManager,
    private val objectMapper: ObjectMapper,
    private val mongoTemplate: MongoTemplate
) {
    private val log = LoggerFactory.getLogger(MeetingService::class.java)

    companion object {
        private const val MEETING_CODE_LENGTH = 6
        private const val ROOM_ID_MIN = 100000
        private const val ROOM_ID_MAX = 999999
        private const val MAX_CODE_RETRIES = 20
        private const val PASSWORD_MIN_LENGTH = 4
        private const val PASSWORD_MAX_LENGTH = 8
        private val PASSWORD_PATTERN = Regex("^\\d{$PASSWORD_MIN_LENGTH,$PASSWORD_MAX_LENGTH}$")
    }

    /**
     * 创建会议，返回 MeetingDto 和创建者的 UserSig。
     * [password] 可选，4-8 位纯数字。
     */
    fun createMeeting(
        userId: String,
        title: String,
        type: Int,
        password: String? = null
    ): Pair<MeetingDto, String> {
        require(title.isNotBlank()) { "会议标题不能为空" }
        require(title.length <= 50) { "会议标题不能超过50个字符" }
        require(type in 0..1) { "无效的会议类型" }

        // 校验密码格式（如果提供了密码）
        if (password != null) {
            require(PASSWORD_PATTERN.matches(password)) {
                "会议密码必须为${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH}位纯数字"
            }
        }

        val meetingCode = generateUniqueMeetingCode()
        val roomId = Random.nextInt(ROOM_ID_MIN, ROOM_ID_MAX + 1)

        val meeting = meetingRepository.save(
            Meeting(
                title = title,
                meetingCode = meetingCode,
                creatorId = userId,
                roomId = roomId,
                type = type,
                password = password,
                status = 0,
                participants = listOf(userId),
                allParticipants = listOf(userId)
            )
        )

        val userSig = meetingTrtcService.genUserSig(userId)
        val dto = toDto(meeting)

        log.info(
            "[会议] 创建成功 meetingId={}, code={}, roomId={}, creator={}, hasPassword={}",
            meeting.id, meetingCode, roomId, userId, password != null
        )
        return Pair(dto, userSig)
    }

    /**
     * 通过会议 ID 加入会议。
     * [password] 可选，当会议设置了密码时需要提供（创建者和已在会议中的参与者免密码）。
     */
    fun joinMeeting(
        userId: String,
        meetingId: String,
        password: String? = null
    ): MeetingJoinResponse {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        // 锁定状态下，仅创建者和已在会议中的参与者可加入
        if (meeting.isLocked && userId != meeting.creatorId && userId !in meeting.participants) {
            throw IllegalStateException("会议已锁定，无法加入")
        }

        // 密码校验：创建者和已在会议中的参与者免密码
        if (meeting.password != null && userId != meeting.creatorId && userId !in meeting.participants) {
            if (password == null) {
                throw MeetingPasswordRequiredException()
            }
            if (password != meeting.password) {
                throw MeetingPasswordIncorrectException()
            }
        }

        val updatedParticipants = if (userId in meeting.participants) {
            meeting.participants
        } else {
            meeting.participants + userId
        }

        val updatedAllParticipants = if (userId in meeting.allParticipants) {
            meeting.allParticipants
        } else {
            meeting.allParticipants + userId
        }

        val updatedMeeting = meeting.copy(
            participants = updatedParticipants,
            allParticipants = updatedAllParticipants,
            lastEmptyAt = null
        )
        meetingRepository.save(updatedMeeting)

        val userSig = meetingTrtcService.genUserSig(userId)
        val creatorName = findDisplayName(meeting.creatorId)

        log.info("[会议] 用户加入 meetingId={}, userId={}", meetingId, userId)

        return MeetingJoinResponse(
            meetingId = meeting.id!!,
            meetingCode = meeting.meetingCode,
            roomId = meeting.roomId,
            userSig = userSig,
            sdkAppId = MeetingTrtcService.MEETING_SDK_APP_ID,
            title = meeting.title,
            creatorId = meeting.creatorId,
            creatorName = creatorName,
            type = meeting.type,
            isLocked = meeting.isLocked,
            createdAt = meeting.createdAt,
        )
    }

    /**
     * 通过会议码或房间号加入会议。
     */
    fun joinByCode(
        userId: String,
        meetingCode: String,
        password: String? = null
    ): MeetingJoinResponse {
        val input = meetingCode.trim()
        require(input.isNotBlank()) { "会议码或房间号不能为空" }
        val meeting = meetingRepository.findByMeetingCode(input)
            ?: input.toIntOrNull()?.let { roomId ->
                meetingRepository.findByRoomId(roomId)
            }
            ?: throw IllegalArgumentException("会议码或房间号无效，未找到对应会议")
        return joinMeeting(userId, meeting.id!!, password)
    }

    /**
     * 离开会议。
     */
    fun leaveMeeting(userId: String, meetingId: String) {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        val updatedParticipants = meeting.participants.filter { it != userId }
        val updatedMeeting = meeting.copy(
            participants = updatedParticipants,
            lastEmptyAt = if (updatedParticipants.isEmpty()) System.currentTimeMillis() else null
        )
        meetingRepository.save(updatedMeeting)

        log.info("[会议] 用户离开 meetingId={}, userId={}, remaining={}", meetingId, userId, updatedParticipants.size)
    }

    /**
     * 结束会议（仅创建者可操作）。
     */
    fun endMeeting(userId: String, meetingId: String) {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        if (meeting.creatorId != userId) {
            throw IllegalStateException("仅会议创建者可以结束会议")
        }

        val endedMeeting = meeting.copy(
            status = 1,
            endedAt = System.currentTimeMillis()
        )
        meetingRepository.save(endedMeeting)
        cleanupChatMessages(meetingId)

        broadcastMeetingEnded(endedMeeting)
        log.info("[会议] 已结束 meetingId={}, endedBy={}", meetingId, userId)
    }

    /**
     * 查询会议详情。
     */
    fun getMeeting(meetingId: String): MeetingDto {
        val meeting = meetingRepository.findById(meetingId).orElse(null)
            ?: throw IllegalArgumentException("会议不存在")
        return toDto(meeting)
    }

    /**
     * 查询用户参与的会议历史（分页）。
     */
    fun getUserMeetingHistory(userId: String, page: Int, size: Int): Pair<List<MeetingDto>, Long> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pageResult = meetingRepository.findUserMeetings(userId, pageable)
        val dtos = pageResult.content.map { toDto(it) }
        return Pair(dtos, pageResult.totalElements)
    }

    /**
     * 获取会议参与者详细列表。
     */
    fun getParticipants(meetingId: String): List<ParticipantDto> {
        val meeting = meetingRepository.findById(meetingId).orElse(null)
            ?: throw IllegalArgumentException("会议不存在")

        return meeting.participants.map { participantId ->
            val user = findUserCompat(participantId)
            ParticipantDto(
                userId = participantId,
                displayName = user?.displayName,
                avatarUrl = user?.avatarUrl,
                isCreator = participantId == meeting.creatorId
            )
        }
    }

    /**
     * 踢出参与者（仅创建者可操作）。
     */
    fun kickParticipant(operatorId: String, meetingId: String, targetUserId: String) {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        if (meeting.creatorId != operatorId) {
            throw IllegalStateException("仅会议创建者可以踢出参与者")
        }

        if (targetUserId == operatorId) {
            throw IllegalArgumentException("不能踢出自己")
        }

        if (targetUserId !in meeting.participants) {
            throw IllegalArgumentException("该用户不在会议中")
        }

        val updatedParticipants = meeting.participants.filter { it != targetUserId }
        val updatedMeeting = meeting.copy(
            participants = updatedParticipants,
            lastEmptyAt = if (updatedParticipants.isEmpty()) System.currentTimeMillis() else null
        )
        meetingRepository.save(updatedMeeting)

        log.info("[会议] 踢出参与者 meetingId={}, operator={}, target={}", meetingId, operatorId, targetUserId)
    }

    /**
     * 锁定会议（仅创建者可操作）。
     */
    fun lockMeeting(userId: String, meetingId: String) {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        if (meeting.creatorId != userId) {
            throw IllegalStateException("仅会议创建者可以锁定会议")
        }

        if (meeting.isLocked) {
            log.info("[会议] 会议已处于锁定状态 meetingId={}", meetingId)
            return
        }

        val lockedMeeting = meeting.copy(isLocked = true)
        meetingRepository.save(lockedMeeting)

        log.info("[会议] 已锁定 meetingId={}, lockedBy={}", meetingId, userId)
    }

    /**
     * 解锁会议（仅创建者可操作）。
     */
    fun unlockMeeting(userId: String, meetingId: String) {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        if (meeting.creatorId != userId) {
            throw IllegalStateException("仅会议创建者可以解锁会议")
        }

        if (!meeting.isLocked) {
            log.info("[会议] 会议已处于解锁状态 meetingId={}", meetingId)
            return
        }

        val unlockedMeeting = meeting.copy(isLocked = false)
        meetingRepository.save(unlockedMeeting)

        log.info("[会议] 已解锁 meetingId={}, unlockedBy={}", meetingId, userId)
    }

    /**
     * 用户完全离线时，从所有活跃会议中移除该参与者。
     * 供 WebSocket 断开时调用，确保参与者列表与实际在线状态一致。
     */
    fun removeUserFromAllActiveMeetings(userId: String) {
        val meetings = meetingRepository.findActiveMeetingsByParticipant(userId)
        for (meeting in meetings) {
            val updatedParticipants = meeting.participants.filter { it != userId }
            val updatedMeeting = meeting.copy(
                participants = updatedParticipants,
                lastEmptyAt = if (updatedParticipants.isEmpty()) System.currentTimeMillis() else null
            )
            meetingRepository.save(updatedMeeting)
            log.info(
                "[会议] 用户断线离开 meetingId={}, userId={}, remaining={}",
                meeting.id, userId, updatedParticipants.size
            )
        }
    }

    /**
     * 根据 meetingId 查询原始 Meeting 实体（供 Handler 使用）。
     */
    fun findById(meetingId: String): Meeting? {
        return meetingRepository.findById(meetingId).orElse(null)
    }

    fun toDto(meeting: Meeting): MeetingDto {
        val creatorName = findDisplayName(meeting.creatorId)
        return MeetingDto(
            id = meeting.id!!,
            meetingCode = meeting.meetingCode,
            title = meeting.title,
            creatorId = meeting.creatorId,
            creatorName = creatorName,
            roomId = meeting.roomId,
            type = meeting.type,
            status = meeting.status,
            isLocked = meeting.isLocked,
            hasPassword = !meeting.password.isNullOrEmpty(),
            participantCount = meeting.participants.size,
            createdAt = meeting.createdAt,
            endedAt = meeting.endedAt
        )
    }

    /**
     * 管理后台：强制结束会议（不校验创建者）。
     */
    fun forceEndMeeting(meetingId: String) {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        val endedMeeting = meeting.copy(
            status = 1,
            endedAt = System.currentTimeMillis()
        )
        meetingRepository.save(endedMeeting)
        cleanupChatMessages(meetingId)

        broadcastMeetingEnded(endedMeeting)
        log.info("[会议] 管理员强制结束 meetingId={}", meetingId)
    }

    /**
     * 管理后台：管理员以观察者身份进入会议（不计入参与者列表）。
     * 仅返回 TRTC 凭证，不修改 participants。
     */
    fun adminJoinMeeting(adminUserId: String, meetingId: String): MeetingJoinResponse {
        val meeting = meetingRepository.findByIdAndStatus(meetingId, 0)
            ?: throw IllegalArgumentException("会议不存在或已结束")

        val userSig = meetingTrtcService.genUserSig(adminUserId)
        val creatorName = findDisplayName(meeting.creatorId)

        log.info("[会议] 管理员进入（观察者） meetingId={}, adminUserId={}", meetingId, adminUserId)

        return MeetingJoinResponse(
            meetingId = meeting.id!!,
            meetingCode = meeting.meetingCode,
            roomId = meeting.roomId,
            userSig = userSig,
            sdkAppId = MeetingTrtcService.MEETING_SDK_APP_ID,
            title = meeting.title,
            creatorId = meeting.creatorId,
            creatorName = creatorName,
            type = meeting.type,
            isLocked = meeting.isLocked,
            createdAt = meeting.createdAt,
        )
    }

    /**
     * 管理后台：分页查询所有会议（可按状态、关键字筛选）。
     */
    fun getAllMeetings(page: Int, size: Int, status: Int?, keyword: String?): Pair<List<MeetingDto>, Long> {
        val pageable = PageRequest.of(page, size)
        val safeKeyword = keyword?.takeIf { it.isNotBlank() }?.escapeRegex()

        val pageResult = when {
            safeKeyword != null && status != null -> meetingRepository.findByTitleRegexAndStatus(safeKeyword, status, pageable)
            safeKeyword != null -> meetingRepository.findByTitleRegex(safeKeyword, pageable)
            status != null -> meetingRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
            else -> meetingRepository.findAllByOrderByCreatedAtDesc(pageable)
        }

        val dtos = pageResult.content.map { toDto(it) }
        return Pair(dtos, pageResult.totalElements)
    }

    /**
     * 管理后台：获取会议历史参与者（所有曾参与过的用户）。
     */
    fun getHistoricalParticipants(meetingId: String): List<ParticipantDto> {
        val meeting = meetingRepository.findById(meetingId).orElse(null)
            ?: throw IllegalArgumentException("会议不存在")

        return meeting.allParticipants.map { participantId ->
            val user = findUserCompat(participantId)
            ParticipantDto(
                userId = participantId,
                displayName = user?.displayName,
                avatarUrl = user?.avatarUrl,
                isCreator = participantId == meeting.creatorId
            )
        }
    }

    /**
     * 向所有曾参与会议的用户广播会议结束事件，
     * 客户端收到后更新分享卡片的显示状态。
     */
    fun broadcastMeetingEnded(meeting: Meeting) {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "type" to "meeting_ended",
                "data" to mapOf("meetingId" to meeting.id)
            )
        )
        for (userId in meeting.allParticipants) {
            userSessionManager.pushToUser(userId, payload, skipApns = true)
        }
    }

    /**
     * 查询用户参与过的、最近 7 天内结束的会议 ID 列表。
     * 供 WebSocket 重连时推送 ended_meetings_sync 使用。
     */
    fun getEndedMeetingIds(userId: String): List<String> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        return meetingRepository.findEndedMeetingsByParticipant(userId, sevenDaysAgo)
            .mapNotNull { it.id }
    }

    /**
     * 获取会议聊天历史记录。
     */
    fun getChatHistory(meetingId: String): List<MeetingChatMessageDto> {
        val meeting = meetingRepository.findById(meetingId).orElse(null)
            ?: throw IllegalArgumentException("会议不存在")
        return meetingChatMessageRepository.findByMeetingIdOrderByCreatedAtAsc(meetingId).map { msg ->
            MeetingChatMessageDto(
                meetingId = msg.meetingId,
                senderId = msg.senderId,
                senderName = msg.senderName,
                content = msg.content,
                timestamp = msg.createdAt
            )
        }
    }

    /**
     * 清理会议聊天消息。
     */
    fun cleanupChatMessages(meetingId: String) {
        meetingChatMessageRepository.deleteByMeetingId(meetingId)
        log.debug("[会议] 已清理聊天消息 meetingId={}", meetingId)
    }

    private fun findDisplayName(userId: String): String? {
        return findUserCompat(userId)?.displayName
    }

    /**
     * 兼容 _id 为 String 或 ObjectId 的用户查找。历史数据两种格式并存。
     */
    private fun findUserCompat(userId: String): User? {
        val direct = userRepository.findById(userId).orElse(null)
        if (direct != null) return direct
        if (!ObjectId.isValid(userId)) return null
        return try {
            val query = Query(Criteria.where("_id").`is`(ObjectId(userId)))
            mongoTemplate.findOne(query, User::class.java)
        } catch (e: Exception) {
            log.warn("findUserCompat failed for $userId", e)
            null
        }
    }

    private fun generateUniqueMeetingCode(): String {
        repeat(MAX_CODE_RETRIES) {
            val code = buildString {
                repeat(MEETING_CODE_LENGTH) {
                    append(Random.nextInt(0, 10))
                }
            }
            if (meetingRepository.findByMeetingCode(code) == null) {
                return code
            }
        }
        throw IllegalStateException("无法生成唯一的会议码，请重试")
    }
}
