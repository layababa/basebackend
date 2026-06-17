package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.AuthResponse
import com.layababateam.xinxiwang_backend.dto.UserDto
import com.layababateam.xinxiwang_backend.dto.UserSummaryDto
import com.layababateam.xinxiwang_backend.dto.UserUpdateRequest
import com.layababateam.xinxiwang_backend.service.UserProfilePort
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userProfilePort: UserProfilePort,
) {
    @PostMapping("/update")
    fun updateProfile(
        request: HttpServletRequest,
        @Valid @RequestBody body: UserUpdateRequest,
    ): ResponseEntity<AuthResponse> {
        val userId = request.getAttribute("userId") as String
        val response = userProfilePort.updateProfile(userId, body)
        return if (response.success) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @GetMapping("/search")
    fun searchUsers(
        request: HttpServletRequest,
        @RequestParam keyword: String,
    ): ResponseEntity<ApiResponse<List<UserSummaryDto>>> {
        if (keyword.isBlank() || keyword.length < 2) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "关键字至少需要2个字符"))
        }
        if (keyword.length > 50) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "关键字不能超过50个字符"))
        }
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(userProfilePort.searchUsers(userId, keyword)))
    }

    @GetMapping("/{id}")
    fun getUserById(
        request: HttpServletRequest,
        @PathVariable id: String,
    ): ResponseEntity<ApiResponse<UserDto>> {
        val userId = request.getAttribute("userId") as String
        val user = userProfilePort.getUserById(userId, id)
            ?: return ResponseEntity.badRequest().body(ApiResponse(false, "用户不存在"))
        return ResponseEntity.ok(ApiResponse.ok(user))
    }
}
