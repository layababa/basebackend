package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.AuthResponse
import com.layababateam.xinxiwang_backend.dto.LoginRequest
import com.layababateam.xinxiwang_backend.dto.RegisterRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity

/** 客户端认证能力端口。SDK 负责认证路由，接入方负责账号、会话和安全策略实现。 */
interface AuthPort {
    fun register(request: RegisterRequest): ResponseEntity<AuthResponse>

    fun login(request: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<AuthResponse>

    fun deleteAccount(request: HttpServletRequest, body: DeleteAccountRequest): ResponseEntity<AuthResponse>
}

data class DeleteAccountRequest(val password: String)
