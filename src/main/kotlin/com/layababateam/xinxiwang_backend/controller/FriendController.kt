package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.FriendAcceptDto
import com.layababateam.xinxiwang_backend.dto.FriendBlockDto
import com.layababateam.xinxiwang_backend.dto.FriendDeleteDto
import com.layababateam.xinxiwang_backend.dto.FriendDto
import com.layababateam.xinxiwang_backend.dto.FriendRejectDto
import com.layababateam.xinxiwang_backend.dto.FriendRequestDto
import com.layababateam.xinxiwang_backend.dto.FriendRequestSendDto
import com.layababateam.xinxiwang_backend.dto.FriendSyncResponse
import com.layababateam.xinxiwang_backend.dto.FriendUnblockDto
import com.layababateam.xinxiwang_backend.service.FriendPort
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/friend")
class FriendController(
    private val friendPort: FriendPort,
) {
    private val log = LoggerFactory.getLogger(FriendController::class.java)

    @GetMapping("/sync")
    fun syncFriends(
        request: HttpServletRequest,
        @RequestParam(defaultValue = "0") afterVersion: Long,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<FriendSyncResponse>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(friendPort.syncFriends(userId, afterVersion, limit)))
    }

    @GetMapping("/check/{userId}")
    fun checkIsFriend(
        request: HttpServletRequest,
        @PathVariable userId: String,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val myUserId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(friendPort.checkIsFriend(myUserId, userId)))
    }

    @GetMapping("/list")
    fun getFriendList(request: HttpServletRequest): ResponseEntity<ApiResponse<List<FriendDto>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(friendPort.getFriendList(userId)))
    }

    @GetMapping("/requests")
    fun getPendingRequests(request: HttpServletRequest): ResponseEntity<ApiResponse<List<FriendRequestDto>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(friendPort.getPendingRequests(userId)))
    }

    @GetMapping("/requests/all")
    fun getAllRequests(request: HttpServletRequest): ResponseEntity<ApiResponse<List<FriendRequestDto>>> {
        val userId = request.getAttribute("userId") as String
        return ResponseEntity.ok(ApiResponse.ok(friendPort.getAllRequests(userId)))
    }

    @PostMapping("/request")
    fun sendFriendRequest(
        request: HttpServletRequest,
        @Valid @RequestBody body: FriendRequestSendDto,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(
                ApiResponse(true, "好友请求已发送",
                    friendPort.sendFriendRequest(userId, body.toUserId, body.message, body.fromGroupId, body.sourceCardMessageId)
                )
            )
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "操作失败"))
        }
    }

    @PostMapping("/accept")
    fun acceptFriendRequest(
        request: HttpServletRequest,
        @Valid @RequestBody body: FriendAcceptDto,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val userId = request.getAttribute("userId") as String
        return try {
            ResponseEntity.ok(ApiResponse(true, "好友请求已接受", friendPort.acceptFriendRequest(userId, body.requestId)))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "操作失败"))
        }
    }

    @PostMapping("/reject")
    fun rejectFriendRequest(
        request: HttpServletRequest,
        @Valid @RequestBody body: FriendRejectDto,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        return try {
            friendPort.rejectFriendRequest(userId, body.requestId, body.permanent)
            ResponseEntity.ok(ApiResponse(true, "好友请求已拒绝"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "操作失败"))
        }
    }

    @Deprecated("Use DELETE /api/friend/{friendId} instead")
    @PostMapping("/delete")
    fun deleteFriendLegacy(
        request: HttpServletRequest,
        @Valid @RequestBody body: FriendDeleteDto,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        log.warn("[DEPRECATED] POST /api/friend/delete called by userId={}, friendId={}", userId, body.friendId)
        friendPort.deleteFriend(userId, body.friendId)
        return ResponseEntity.ok(ApiResponse(true, "好友已删除"))
    }

    @DeleteMapping("/{friendId}")
    fun deleteFriend(
        request: HttpServletRequest,
        @PathVariable friendId: String,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        friendPort.deleteFriend(userId, friendId)
        return ResponseEntity.ok(ApiResponse(true, "好友已删除"))
    }

    @PostMapping("/block")
    fun blockFriend(
        request: HttpServletRequest,
        @Valid @RequestBody body: FriendBlockDto,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        return try {
            friendPort.blockFriend(userId, body.friendId)
            ResponseEntity.ok(ApiResponse(true, "已拉黑"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "操作失败"))
        }
    }

    @PostMapping("/unblock")
    fun unblockFriend(
        request: HttpServletRequest,
        @Valid @RequestBody body: FriendUnblockDto,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = request.getAttribute("userId") as String
        return try {
            friendPort.unblockFriend(userId, body.friendId)
            ResponseEntity.ok(ApiResponse(true, "已解除拉黑"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ApiResponse(false, e.message ?: "操作失败"))
        }
    }
}
