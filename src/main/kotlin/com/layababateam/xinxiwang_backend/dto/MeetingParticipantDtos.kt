package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty

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
