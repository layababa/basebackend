package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.service.TrtcUserSigProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/trtc")
class TrtcController(
    private val trtcUserSigProvider: TrtcUserSigProvider
) {

    @GetMapping("/usersig")
    fun getUserSig(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String

        val userSig = trtcUserSigProvider.genUserSig(userId)
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "sdkAppId" to trtcUserSigProvider.sdkAppId,
                    "userId" to userId,
                    "userSig" to userSig
                )
            )
        )
    }
}
