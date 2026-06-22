package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class ParticipantDto(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    @get:JsonProperty("isCreator")
    val isCreator: Boolean,
    @get:JsonProperty("isAdmin")
    val isAdmin: Boolean = false,
    @get:JsonProperty("isManagementOwner")
    val isManagementOwner: Boolean = false,
    @get:JsonProperty("chatDenied")
    val chatDenied: Boolean = false,
    @get:JsonProperty("linkMicDenied")
    val linkMicDenied: Boolean = false,
    @get:JsonProperty("screenShareDenied")
    val screenShareDenied: Boolean = false,
    @get:JsonProperty("isMuted")
    val isMuted: Boolean = false,
    @get:JsonProperty("isVirtual")
    val isVirtual: Boolean = false,
)

data class AddVirtualParticipantsRequest(
    @field:Min(0, message = "虚拟成员数量不能小于0")
    @field:Max(100, message = "虚拟成员数量不能超过100")
    val count: Int,
)

data class MeetingRemovalRestrictionDto(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val removedAt: Long,
    val expiresAt: Long,
    val remainingMillis: Long,
)

data class KickParticipantRequest(
    val userId: String,
)
