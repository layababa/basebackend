package com.layababateam.xinxiwang_backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "meetings")
data class Meeting(
    @Id val id: String? = null,
    val title: String,
    @Indexed(unique = true) val meetingCode: String,
    val creatorId: String,
    @Indexed(unique = true) val roomId: Int,
    val type: Int = 0,
    val status: Int = 0,
    val participants: List<String> = emptyList(),
    val allParticipants: List<String> = emptyList(),
    /** 大会议管理员列表；房主不放入该列表，便于保留房主最高权限。 */
    val adminIds: List<String> = emptyList(),
    val password: String? = null,
    /** 密码版本只用于识别旧邀请入口，不向普通用户暴露最新密码内容。 */
    val passwordVersion: Long = 0,
    val isLocked: Boolean = false,
    /** 被房主/管理员移出后的临时限制名单；仅作用于当前会议，过期后自动允许重新入会。 */
    val removalRestrictions: List<MeetingRemovalRestriction> = emptyList(),
    /** 被人工恢复入会的用户；重新进入当前会议时免一次密码校验的业务凭据。 */
    val restoredParticipants: List<String> = emptyList(),
    /** 直播连麦申请落库，保证房主离开后重进仍能看到未处理申请。 */
    val linkMicRequests: List<LinkMicRequestState> = emptyList(),
    /** 当前连麦中的观众状态，供重连/切后台恢复角色使用。 */
    val linkedMicUsers: List<LinkMicRequestState> = emptyList(),
    /** 本场会议内已通过连麦的观众；只要未离会，再次连麦可直接恢复，不重复审批。 */
    val approvedLinkMicUsers: List<LinkMicRequestState> = emptyList(),
    /** 宣讲会权限设置只跟随当前会议，会议结束或用户离会后不继承到下一场。 */
    val permissionSettings: MeetingPermissionSettings = MeetingPermissionSettings(),
    /** 聊天/连麦/共享的待审批申请；只保存未处理项，便于申请管理增量展示。 */
    val permissionRequests: List<MeetingPermissionRequestState> = emptyList(),
    /** 本场会议内已单独授权的普通用户；离会会被清理，重进后必须重新按规则判断。 */
    val permissionGrants: List<MeetingPermissionGrantState> = emptyList(),
    /** 当前正在屏幕共享的用户，同一时间只允许一个人占用该能力。 */
    val activeScreenShareUserId: String? = null,
    /** 当前屏幕共享开始时间，供客户端申请管理和排查共享占用使用。 */
    val activeScreenShareStartedAt: Long? = null,
    /** 预约宣讲会开始时间；非预约会议为空。 */
    val scheduledStartAt: Long? = null,
    /** 旧客户端兼容字段；新版预约宣讲会不再展示或使用会议时长。 */
    val scheduledDurationMinutes: Int? = null,
    /** 预约时区 ID，例如 GMT+08:00。 */
    val scheduledTimeZone: String? = null,
    /** 是否循环会议；当前固定会议号仍只服务本次预约，循环标记仅做预约信息展示。 */
    val recurring: Boolean = false,
    /** 循环会议展示规则：never/daily/weekly/biweekly/monthly。 */
    val recurringRule: String = "never",
    /** 循环会议自动生成的下一场会记录源会议 ID；同一源会议只能生成一条下一场。 */
    val recurringSourceMeetingId: String? = null,
    /** 预约邀请用户。 */
    val invitedUserIds: List<String> = emptyList(),
    /** 用户对预约会议的备注，作为列表菜单里的轻量记录。 */
    val remark: String? = null,
    /** 主持人开启会议后是否允许普通成员继续进入。 */
    val allowJoinAfterStart: Boolean = true,
    /** 会议信息版本；预约创建为 1，修改基本信息后递增，用于旧分享入口提示“会议已更改”。 */
    val scheduleVersion: Long = 0,
    val scheduleUpdatedAt: Long? = null,
    /** 预约开始前 1 分钟提醒创建者，只记录发送时间，避免定时任务重复推送。 */
    val scheduleReminderSentAt: Long? = null,
    /** 到达预约时长后提醒当前主持人延长/结束，只记录发送时间，避免每分钟重复弹窗。 */
    val durationPromptSentAt: Long? = null,
    val canceledAt: Long? = null,
    /** 当前管理权持有人；同一场宣讲会任意时刻只允许一个人持有管理权。 */
    val managementOwnerId: String? = null,
    /** 当前主持人顺延按真实进入房间顺序排序；创建者仅在未主动离开时优先。 */
    val participantJoinOrder: List<String> = emptyList(),
    val startedAt: Long? = null,
    /** 管理后台调试用的 TRTC 参会时长段。精确账单仍以腾讯云控制台为准。 */
    val participantSegments: List<MeetingParticipantSegment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val lastEmptyAt: Long? = null
)

data class MeetingParticipantSegment(
    val userId: String,
    /** 视频订阅段中表示被订阅/被观看的用户；语音参会段为空。 */
    val sourceUserId: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val leftAt: Long? = null,
    /** audio/video；会议目前无服务端分辨率回调，默认按 audio 统计，后续可由 TRTC 用量明细修正。 */
    val mediaType: String = "audio",
    /** TRTC 套餐包抵扣分档：AUDIO/SD/HD/FHD/2K/4K。 */
    val tier: String = "AUDIO",
    /** 真实分辨率的宽；旧数据为空时继续走估算分档。 */
    val videoWidth: Int? = null,
    /** 真实分辨率的高；旧数据为空时继续走估算分档。 */
    val videoHeight: Int? = null,
    /** TRTC 流类型：big / small / sub；旧数据为空时默认按 camera 视频看待。 */
    val streamType: String? = null,
    /** true 表示缺少真实分辨率或旧数据回退估算；false 表示真实事件段。 */
    val estimated: Boolean = true
)

data class LinkMicRequestState(
    val userId: String,
    val userName: String = "",
    val userAvatar: String = "",
    /** 连麦成员麦克风开关状态，左下角头像/视频只有开麦、开摄像头或共享时展示。 */
    val micOn: Boolean = false,
    /** 连麦成员摄像头状态，供房主端重进或 TRTC 视频回调漏失时恢复远端视频卡片。 */
    val cameraOn: Boolean = false,
    val requestedAt: Long = System.currentTimeMillis()
)

data class MeetingPermissionSettings(
    /** 会议级聊天开关，仅限制普通用户，房主/管理员不受影响。 */
    val allowChat: Boolean = false,
    /** 会议级连麦开放开关；开启后普通用户可直接开麦/开摄像头，关闭时走申请授权。 */
    val allowLinkMic: Boolean = false,
    /** 会议级屏幕共享申请开关，仅限制普通用户，屏幕共享仍走独立排他裁决。 */
    val allowScreenShare: Boolean = true,
    /** 单个用户禁止聊天名单；比会议级允许开关优先级更高。 */
    val deniedChatUsers: List<String> = emptyList(),
    /** 单个用户禁止连麦名单；关闭后会立即清理连麦/授权状态。 */
    val deniedLinkMicUsers: List<String> = emptyList(),
    /** 单个用户禁止共享名单；关闭后会立即结束该用户共享。 */
    val deniedScreenShareUsers: List<String> = emptyList(),
    /** 主画面“一键关闭用户连麦”状态；开启时普通用户不能再申请或开启连麦。 */
    val linkMicLockedByQuickAction: Boolean = false,
    /** 一键关闭前的聊天公共开关，用于再次点击时恢复原始会议规则。 */
    val quickActionPreviousAllowChat: Boolean? = null,
    /** 一键关闭前的连麦公共开关，用于再次点击时恢复原始会议规则。 */
    val quickActionPreviousAllowLinkMic: Boolean? = null
)

data class MeetingPermissionRequestState(
    val userId: String,
    /** 权限类型：chat/link_mic/screen_share，屏幕共享独立于发言权限。 */
    val type: String,
    val userName: String = "",
    val userAvatar: String = "",
    val requestedAt: Long = System.currentTimeMillis()
)

data class MeetingPermissionGrantState(
    val userId: String,
    /** 权限类型：chat/link_mic/screen_share，单独授权高于会议级允许开关。 */
    val type: String,
    val userName: String = "",
    val userAvatar: String = "",
    /** 保留原申请时间，申请管理列表按申请顺序固定展示。 */
    val requestedAt: Long = System.currentTimeMillis(),
    val grantedAt: Long = System.currentTimeMillis()
)

data class MeetingRemovalRestriction(
    val userId: String,
    val removedBy: String,
    val removedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
)
