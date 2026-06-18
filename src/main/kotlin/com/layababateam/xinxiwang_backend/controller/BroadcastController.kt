package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.BarrageCheckRequest
import com.layababateam.xinxiwang_backend.dto.CreateBroadcastRequest
import com.layababateam.xinxiwang_backend.dto.CreateLuckyBagRequest
import com.layababateam.xinxiwang_backend.dto.CreateRedPacketRequest
import com.layababateam.xinxiwang_backend.dto.EditBroadcastRequest
import com.layababateam.xinxiwang_backend.dto.JoinBroadcastRequest
import com.layababateam.xinxiwang_backend.dto.LikeReportRequest
import com.layababateam.xinxiwang_backend.dto.TransferSpeakerRequest
import com.layababateam.xinxiwang_backend.dto.UserIdRequest
import com.layababateam.xinxiwang_backend.service.BroadcastPort
import com.layababateam.xinxiwang_backend.service.StringListRules
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("legacy-broadcast")
@RequestMapping("/api/broadcast")
class BroadcastController(
    private val broadcastPort: BroadcastPort,
) {
    private fun uid(request: HttpServletRequest): String = request.getAttribute("userId") as String

    @PostMapping("/broadcasts")
    fun create(
        @RequestBody body: CreateBroadcastRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.create(uid(request), body)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @GetMapping("/broadcasts/{id}")
    fun get(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.get(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/subscribe")
    fun subscribe(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.subscribe(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @DeleteMapping("/broadcasts/{id}/subscribe")
    fun unsubscribe(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.unsubscribe(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @GetMapping("/broadcasts")
    fun list(
        @RequestParam(required = false, defaultValue = "live") scope: String,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
    ): ResponseEntity<ApiResponse<*>> {
        val pageData = broadcastPort.list(scope, page, pageSize)
        return ResponseEntity.ok(
            ApiResponse.ok(
                mapOf(
                    "total" to pageData.total,
                    "page" to page,
                    "pageSize" to pageSize,
                    "list" to pageData.content,
                )
            )
        )
    }

    @PostMapping("/broadcasts/{id}/join")
    fun join(
        @PathVariable id: String,
        @RequestBody(required = false) body: JoinBroadcastRequest?,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val response = broadcastPort.join(uid(request), id, body?.password, body?.forceLeaveOther ?: false)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/broadcasts/{id}/leave")
    fun leave(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.leave(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @PostMapping("/broadcasts/{id}/start-streaming")
    fun startStreaming(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.startStreaming(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/end")
    fun end(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.end(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.cancel(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PatchMapping("/broadcasts/{id}")
    fun edit(
        @PathVariable id: String,
        @RequestBody body: EditBroadcastRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.edit(uid(request), id, body)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @GetMapping("/broadcasts/{id}/viewers")
    fun viewers(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<ApiResponse<*>> {
        val list = broadcastPort.viewers(id, page, pageSize, filter, keyword)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("list" to list)))
    }

    @PostMapping("/broadcasts/{id}/kick")
    fun kick(
        @PathVariable id: String,
        @RequestBody body: UserIdRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.kick(uid(request), id, body.userId)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/mute")
    fun mute(
        @PathVariable id: String,
        @RequestBody body: UserIdRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.mute(uid(request), id, body.userId)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/unmute")
    fun unmute(
        @PathVariable id: String,
        @RequestBody body: UserIdRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.unmute(uid(request), id, body.userId)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/mute-all")
    fun muteAll(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.muteAll(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/unmute-all")
    fun unmuteAll(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.unmuteAll(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/transfer-speaker")
    fun transferSpeaker(
        @PathVariable id: String,
        @RequestBody body: TransferSpeakerRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val dto = broadcastPort.transferSpeaker(uid(request), id, body.newSpeakerId)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("broadcast" to dto)))
    }

    @PostMapping("/broadcasts/{id}/raise-hand")
    fun raiseHand(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val queuePosition = broadcastPort.raiseHand(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("queuePosition" to queuePosition)))
    }

    @DeleteMapping("/broadcasts/{id}/raise-hand")
    fun cancelRaiseHand(
        @PathVariable id: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.cancelRaiseHand(uid(request), id)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @PostMapping("/broadcasts/{id}/link-mic/approve")
    fun approveLinkMic(
        @PathVariable id: String,
        @RequestBody body: UserIdRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.approveLinkMic(uid(request), id, body.userId)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @PostMapping("/broadcasts/{id}/link-mic/reject")
    fun rejectLinkMic(
        @PathVariable id: String,
        @RequestBody body: UserIdRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.rejectLinkMic(uid(request), id, body.userId, body.reason)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @PostMapping("/broadcasts/{id}/link-mic/remove")
    fun removeLinkMic(
        @PathVariable id: String,
        @RequestBody body: UserIdRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.removeLinkMic(uid(request), id, body.userId)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @GetMapping("/broadcasts/{id}/link-mic")
    fun linkMicState(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(broadcastPort.linkMicState(id)))
    }

    @PostMapping("/broadcasts/{id}/barrage")
    fun barrage(
        @PathVariable id: String,
        @RequestBody body: BarrageCheckRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        val response = broadcastPort.checkBarrage(uid(request), id, body.content)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/broadcasts/{id}/like")
    fun like(
        @PathVariable id: String,
        @RequestBody body: LikeReportRequest,
    ): ResponseEntity<ApiResponse<*>> {
        broadcastPort.incrementLikes(id, body.count)
        return ResponseEntity.ok(ApiResponse.ok<Map<String, Any>>(emptyMap()))
    }

    @PostMapping("/broadcasts/{id}/red-packets")
    fun createRedPacket(
        @PathVariable id: String,
        @RequestBody body: CreateRedPacketRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(broadcastPort.createRedPacket(uid(request), id, body)))
    }

    @PostMapping("/broadcasts/{id}/lucky-bags")
    fun createLuckyBag(
        @PathVariable id: String,
        @RequestBody body: CreateLuckyBagRequest,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.ok(ApiResponse.ok(broadcastPort.createLuckyBag(uid(request), id, body)))
    }

    @GetMapping("/broadcasts/{id}/card-snapshot")
    fun cardSnapshot(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val snapshots = broadcastPort.cardSnapshots(listOf(id))
        return ResponseEntity.ok(ApiResponse.ok(mapOf("snapshots" to snapshots)))
    }

    @GetMapping("/broadcasts/card-snapshot")
    fun cardSnapshotBatch(@RequestParam ids: String): ResponseEntity<ApiResponse<*>> {
        val list = StringListRules.delimited(ids)
        return ResponseEntity.ok(ApiResponse.ok(mapOf("snapshots" to broadcastPort.cardSnapshots(list))))
    }
}
