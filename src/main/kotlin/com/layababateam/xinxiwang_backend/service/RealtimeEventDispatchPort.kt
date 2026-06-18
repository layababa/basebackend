package com.layababateam.xinxiwang_backend.service

interface RealtimeEventDispatchPort {
    fun pushToUsers(type: String, targetUserIds: List<String>, payloadJson: String)

    fun pushToGroupMembers(type: String, memberIds: List<String>, payloadJson: String)
}
