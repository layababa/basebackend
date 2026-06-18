package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CreateMeetingRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.ExtendMeetingRequest
import com.layababateam.xinxiwang_backend.dto.JoinByCodeRequest
import com.layababateam.xinxiwang_backend.dto.JoinByIdRequest
import com.layababateam.xinxiwang_backend.dto.KickParticipantRequest
import com.layababateam.xinxiwang_backend.dto.MeetingChatMessageDto
import com.layababateam.xinxiwang_backend.dto.MeetingDto
import com.layababateam.xinxiwang_backend.dto.MeetingJoinResponse
import com.layababateam.xinxiwang_backend.dto.MeetingRemovalRestrictionDto
import com.layababateam.xinxiwang_backend.dto.MeetingShareSnapshotRequest
import com.layababateam.xinxiwang_backend.dto.MeetingShareSnapshotResponse
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.dto.ParticipantDto
import com.layababateam.xinxiwang_backend.dto.ScheduleMeetingRequest
import com.layababateam.xinxiwang_backend.dto.SetMeetingAdminRequest
import com.layababateam.xinxiwang_backend.dto.UpdateScheduleMeetingRequest
import com.layababateam.xinxiwang_backend.dto.UpdateScheduleSettingsRequest
import com.layababateam.xinxiwang_backend.service.ActiveCreatorMeetingExistsPortException
import com.layababateam.xinxiwang_backend.service.MeetingClientCompatibilityPort
import com.layababateam.xinxiwang_backend.service.CreatorScheduledMeetingExistsPortException
import com.layababateam.xinxiwang_backend.service.MeetingPasswordIncorrectPortException
import com.layababateam.xinxiwang_backend.service.MeetingPasswordRequiredPortException
import com.layababateam.xinxiwang_backend.service.MeetingPort
import com.layababateam.xinxiwang_backend.service.MeetingTrtcService
import com.layababateam.xinxiwang_backend.service.PaginationRules
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/meeting")
class MeetingController(
    private val meetingPort: MeetingPort,
    private val meetingClientCompatibilityPort: MeetingClientCompatibilityPort
) {
    private val log = LoggerFactory.getLogger(MeetingController::class.java)

    @PostMapping("/create")
    fun create(
        @Valid @RequestBody req: CreateMeetingRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val (meetingDto, userSig) = meetingPort.createMeeting(
                userId,
                req.title,
                req.type,
                req.password,
                // 新规则下普通用户文字聊天和默认开放连麦均默认关闭；旧客户端不传时也按新默认值创建。
                allowChat = req.allowChat ?: false,
                allowLinkMic = req.allowLinkMic ?: false,
                allowScreenShare = req.allowScreenShare ?: true,
                // 兼容客户端 closeExisting 传 null 的情况，只有明确 true 才执行替换已有会议。
                closeExisting = req.closeExisting == true
            )
            ResponseEntity.ok(
                ApiResponse.ok(
                    mapOf(
                        "meeting" to meetingDto,
                        "userSig" to userSig,
                        "sdkAppId" to MeetingTrtcService.MEETING_SDK_APP_ID
                    )
                )
            )
        } catch (e: ActiveCreatorMeetingExistsPortException) {
            // 单房主单会议冲突返回现有会议，前端用它展示确认替换弹窗。
            ResponseEntity.status(409).body(
                ApiResponse(
                    success = false,
                    message = "当前已有一个会议正在进行中",
                    data = mapOf("meeting" to e.meeting),
                    code = ErrorCode.MEETING_ACTIVE_EXISTS.code
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "参数错误"))
        }
    }

    @PostMapping("/schedule")
    fun schedule(
        @Valid @RequestBody req: ScheduleMeetingRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error<Any>(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            val dto = meetingPort.scheduleMeeting(
                userId = userId,
                title = req.title,
                password = req.password,
                startAt = req.startAt,
                durationMinutes = req.durationMinutes,
                timeZone = req.timeZone,
                recurring = req.recurring,
                recurringRule = req.recurringRule,
                invitedUserIds = req.invitedUserIds,
                remark = req.remark,
                allowJoinAfterStart = req.allowJoinAfterStart ?: true,
                allowChat = req.allowChat ?: false,
                allowLinkMic = req.allowLinkMic ?: false,
                allowScreenShare = req.allowScreenShare ?: true
            )
            ResponseEntity.ok(ApiResponse.ok<Any>(dto))
        } catch (e: CreatorScheduledMeetingExistsPortException) {
            ResponseEntity.status(409).body(
                ApiResponse(
                    success = false,
                    message = e.message ?: "当前已有预约宣讲会",
                    data = e.meeting,
                    code = ErrorCode.MEETING_ACTIVE_EXISTS.code
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(ErrorCode.INVALID_PARAM, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PutMapping("/{meetingId}/schedule")
    fun updateSchedule(
        @PathVariable meetingId: String,
        @Valid @RequestBody req: UpdateScheduleMeetingRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            val dto = meetingPort.updateScheduledMeeting(
                userId = userId,
                meetingId = meetingId,
                title = req.title,
                password = req.password,
                startAt = req.startAt,
                durationMinutes = req.durationMinutes,
                timeZone = req.timeZone,
                recurring = req.recurring,
                recurringRule = req.recurringRule,
                invitedUserIds = req.invitedUserIds,
                remark = req.remark,
                allowJoinAfterStart = req.allowJoinAfterStart ?: true,
                allowChat = req.allowChat ?: false,
                allowLinkMic = req.allowLinkMic ?: false,
                allowScreenShare = req.allowScreenShare ?: true
            )
            ResponseEntity.ok(ApiResponse.ok(dto))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_PARAM, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PutMapping("/{meetingId}/schedule/settings")
    fun updateScheduleSettings(
        @PathVariable meetingId: String,
        @RequestBody req: UpdateScheduleSettingsRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            val dto = meetingPort.updateScheduledMeetingSettings(
                userId = userId,
                meetingId = meetingId,
                allowChat = req.allowChat,
                allowLinkMic = req.allowLinkMic,
                allowJoinAfterStart = req.allowJoinAfterStart
            )
            ResponseEntity.ok(ApiResponse.ok(dto))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_PARAM, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/cancel")
    fun cancelSchedule(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            val dto = meetingPort.cancelScheduledMeeting(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok<MeetingDto>(dto, message = "会议已取消"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/extend")
    fun extend(
        @PathVariable meetingId: String,
        @RequestBody(required = false) req: ExtendMeetingRequest?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            val dto = meetingPort.extendMeeting(
                userId = userId,
                meetingId = meetingId,
                extraMinutes = req?.extraMinutes ?: 30
            )
            ResponseEntity.ok(ApiResponse.ok(dto, message = "会议已延长"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_PARAM, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/start")
    fun startSchedule(
        @PathVariable meetingId: String,
        @RequestBody(required = false) req: JoinByIdRequest?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingJoinResponse>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            val response = meetingPort.startScheduledMeeting(
                userId,
                meetingId,
                req?.password,
                inviteToken = req?.inviteToken
            )
            ResponseEntity.ok(ApiResponse.ok(response))
        } catch (e: MeetingPasswordRequiredPortException) {
            ResponseEntity.status(403).body(
                ApiResponse.error(ErrorCode.MEETING_PASSWORD_REQUIRED, e.message)
            )
        } catch (e: MeetingPasswordIncorrectPortException) {
            ResponseEntity.status(403).body(
                ApiResponse.error(ErrorCode.MEETING_PASSWORD_INCORRECT, e.message)
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/{meetingId}/waiting")
    fun waitingById(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(ApiResponse.ok(meetingPort.getWaitingMeetingById(userId, meetingId)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        }
    }

    @GetMapping("/{meetingId}/reservation")
    fun reservationById(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(ApiResponse.ok(meetingPort.getReservationMeetingById(userId, meetingId)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        }
    }

    @GetMapping("/waiting/by-code/{code}")
    fun waitingByCode(
        @PathVariable code: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(ApiResponse.ok(meetingPort.getWaitingMeetingByCode(userId, code)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        }
    }

    @GetMapping("/reservation/by-code/{code}")
    fun reservationByCode(
        @PathVariable code: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(ApiResponse.ok(meetingPort.getReservationMeetingByCode(userId, code)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        }
    }

    @PostMapping("/{meetingId}/waiting/share-snapshot")
    fun waitingByShareSnapshot(
        @PathVariable meetingId: String,
        @RequestBody(required = false) req: MeetingShareSnapshotRequest?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingShareSnapshotResponse>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(
                ApiResponse.ok(
                    meetingPort.getWaitingMeetingWithShareSnapshot(
                        userId,
                        meetingId,
                        req ?: MeetingShareSnapshotRequest()
                    )
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        }
    }

    @PostMapping("/{meetingId}/reservation/share-snapshot")
    fun reservationByShareSnapshot(
        @PathVariable meetingId: String,
        @RequestBody(required = false) req: MeetingShareSnapshotRequest?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingShareSnapshotResponse>> {
        if (!meetingClientCompatibilityPort.supportsMeetingSchedule(request)) {
            return ResponseEntity.status(426).body(
                ApiResponse.error(ErrorCode.FORBIDDEN, meetingClientCompatibilityPort.meetingScheduleUpdateMessage)
            )
        }
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(
                ApiResponse.ok(
                    meetingPort.getReservationMeetingWithShareSnapshot(
                        userId,
                        meetingId,
                        req ?: MeetingShareSnapshotRequest()
                    )
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        }
    }

    @PostMapping("/{meetingId}/join")
    fun join(
        @PathVariable meetingId: String,
        @RequestBody(required = false) req: JoinByIdRequest?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingJoinResponse>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val response = meetingPort.joinMeeting(
                userId,
                meetingId,
                req?.password,
                fromInvite = req?.fromInvite == true,
                // 邀请凭证由后端校验来源角色，避免普通成员链接绕过会议密码。
                inviteToken = req?.inviteToken
            )
            ResponseEntity.ok(ApiResponse.ok(response))
        } catch (e: MeetingPasswordRequiredPortException) {
            ResponseEntity.status(403).body(
                ApiResponse.error(ErrorCode.MEETING_PASSWORD_REQUIRED, e.message)
            )
        } catch (e: MeetingPasswordIncorrectPortException) {
            ResponseEntity.status(403).body(
                ApiResponse.error(ErrorCode.MEETING_PASSWORD_INCORRECT, e.message)
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/join-by-code")
    fun joinByCode(
        @Valid @RequestBody req: JoinByCodeRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingJoinResponse>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val response = meetingPort.joinByCode(
                userId,
                req.meetingCode,
                req.password,
                inviteToken = req.inviteToken
            )
            ResponseEntity.ok(ApiResponse.ok(response))
        } catch (e: MeetingPasswordRequiredPortException) {
            ResponseEntity.status(403).body(
                ApiResponse.error(ErrorCode.MEETING_PASSWORD_REQUIRED, e.message)
            )
        } catch (e: MeetingPasswordIncorrectPortException) {
            ResponseEntity.status(403).body(
                ApiResponse.error(ErrorCode.MEETING_PASSWORD_INCORRECT, e.message)
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/leave")
    fun leave(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            meetingPort.leaveMeeting(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok<Any>(message = "已离开会议"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "操作失败"))
        }
    }

    @PostMapping("/{meetingId}/end")
    fun end(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            meetingPort.endMeeting(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok<Any>(message = "会议已结束"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "操作失败"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/{meetingId}")
    fun get(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val dto = meetingPort.getMeeting(
                userId,
                meetingId,
                includeScheduledFields = meetingClientCompatibilityPort.supportsMeetingSchedule(request)
            )
            ResponseEntity.ok(ApiResponse.ok(dto))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/{meetingId}/participants")
    fun getParticipants(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<ParticipantDto>>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val participants = meetingPort.getParticipants(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok(participants))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/{meetingId}/chat-history")
    fun getChatHistory(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<MeetingChatMessageDto>>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val history = meetingPort.getChatHistory(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok(history))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/kick")
    fun kick(
        @PathVariable meetingId: String,
        @Valid @RequestBody req: KickParticipantRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            meetingPort.kickParticipant(userId, meetingId, req.userId)
            ResponseEntity.ok(ApiResponse.ok<Any>(message = "已踢出参与者"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "操作失败"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/{meetingId}/removed")
    fun getRemovedParticipants(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<List<MeetingRemovalRestrictionDto>>> {
        val userId = request.getAttribute("userId") as String
        return try {
            val restrictions = meetingPort.getRemovalRestrictions(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok(restrictions))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.NOT_FOUND, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/removed/{targetUserId}/restore")
    fun restoreRemovedParticipant(
        @PathVariable meetingId: String,
        @PathVariable targetUserId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            // 恢复入会只解除当前会议临时限制；用户重新进入当前会议时免再次输入密码。
            meetingPort.restoreRemovedParticipant(userId, meetingId, targetUserId)
            ResponseEntity.ok(ApiResponse.ok<Any>(message = "已恢复入会"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "操作失败"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/lock")
    fun lock(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            meetingPort.lockMeeting(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok<Any>(message = "会议已锁定"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "操作失败"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/unlock")
    fun unlock(
        @PathVariable meetingId: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return try {
            meetingPort.unlockMeeting(userId, meetingId)
            ResponseEntity.ok(ApiResponse.ok<Any>(message = "会议已解锁"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error<Any>(e.message ?: "操作失败"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error<Any>(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @PostMapping("/{meetingId}/admin")
    fun setAdmin(
        @PathVariable meetingId: String,
        @Valid @RequestBody req: SetMeetingAdminRequest,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<MeetingDto>> {
        val userId = request.getAttribute("userId") as String
        return try {
            // 大会议管理员由房主在参与者栏直接设置/取消，不引入额外审核流。
            val dto = meetingPort.setMeetingAdmin(userId, meetingId, req.userId, req.isAdmin)
            ResponseEntity.ok(ApiResponse.ok(dto))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_PARAM, e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(403).body(ApiResponse.error(ErrorCode.FORBIDDEN, e.message))
        }
    }

    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<PagedData<MeetingDto>>> {
        val userId = request.getAttribute("userId") as String
        val clampedSize = PaginationRules.pageSize(size, 50)
        val clampedPage = PaginationRules.zeroBasedPage(page)
        val (items, total) = meetingPort.getUserMeetingHistory(
            userId,
            clampedPage,
            clampedSize,
            includeScheduledMeetings = meetingClientCompatibilityPort.supportsMeetingSchedule(request)
        )
        return ResponseEntity.ok(
            ApiResponse.ok(
                PagedData(
                    items = items,
                    total = total,
                    page = clampedPage,
                    size = clampedSize
                )
            )
        )
    }

}
