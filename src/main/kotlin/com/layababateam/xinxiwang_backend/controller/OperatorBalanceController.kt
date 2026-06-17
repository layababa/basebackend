package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.CreditOperatorPointsRequest
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.OperatorBalanceDto
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.service.OperatorBalancePort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("legacy-broadcast")
@RequestMapping("/api/broadcast/operator")
class OperatorBalanceController(
    private val operatorBalancePort: OperatorBalancePort,
) {
    @GetMapping("/balance")
    fun balance(request: HttpServletRequest): ResponseEntity<ApiResponse<*>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(OperatorBalanceDto(operatorBalancePort.getBalance(userId))))
    }

    @PostMapping("/credit")
    fun credit(
        @RequestBody body: CreditOperatorPointsRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        if (body.amount <= 0) {
            throw BusinessException(ErrorCode.BROADCAST_AMOUNT_INVALID, "充值积分必须大于 0")
        }
        val userId = request.getAttribute("userId") as String
        val newBalance = operatorBalancePort.credit(userId, body.amount, body.reason)
        return ResponseEntity.ok(ApiResponse.ok(OperatorBalanceDto(newBalance)))
    }
}
