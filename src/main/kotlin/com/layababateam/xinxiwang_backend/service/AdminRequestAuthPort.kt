package com.layababateam.xinxiwang_backend.service

data class AdminAuthContext(
    val adminId: String,
    val username: String,
    val role: String,
    val mustChangePassword: Boolean,
)

interface AdminRequestAuthPort {
    fun authenticateAdminRequest(token: String): AdminAuthContext?
}
