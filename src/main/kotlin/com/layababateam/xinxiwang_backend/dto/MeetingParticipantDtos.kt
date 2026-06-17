package com.layababateam.xinxiwang_backend.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 会议参与者详细信息 DTO。
 */
data class ParticipantDto(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    @get:JsonProperty("isCreator")
    val isCreator: Boolean,
    @get:JsonProperty("isMuted")
    val isMuted: Boolean = false
)

/**
 * 踢出参与者请求体。
 */
data class KickParticipantRequest(
    val userId: String
)
