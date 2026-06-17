package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.AuthResponse
import com.layababateam.xinxiwang_backend.dto.LoginRequest
import com.layababateam.xinxiwang_backend.dto.RegisterRequest
import com.layababateam.xinxiwang_backend.service.AuthPort
import com.layababateam.xinxiwang_backend.service.DeleteAccountRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authPort: AuthPort,
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
        authPort.register(request)

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<AuthResponse> =
        authPort.login(request, httpRequest)

    @DeleteMapping("/delete-account")
    fun deleteAccount(
        request: HttpServletRequest,
        @RequestBody body: DeleteAccountRequest,
    ): ResponseEntity<AuthResponse> =
        authPort.deleteAccount(request, body)
}
