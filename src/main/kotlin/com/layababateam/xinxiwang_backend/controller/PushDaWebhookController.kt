package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.model.PushDaBinding
import com.layababateam.xinxiwang_backend.repository.PushDaBindingRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/webhook/pushda")
class PushDaWebhookController(
    private val pushDaBindingRepository: PushDaBindingRepository
) {
    @PostMapping("/bindok")
    fun onBindOk(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        val bindingUid = body["bindingUid"]
            ?: return ResponseEntity.badRequest().build()
        val imUid = body["imUid"]
            ?: return ResponseEntity.badRequest().build()

        val existing = pushDaBindingRepository.findByBindingUid(bindingUid)
        if (existing != null) return ResponseEntity.ok().build()

        pushDaBindingRepository.save(
            PushDaBinding(userId = imUid, bindingUid = bindingUid)
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/unbind")
    fun onUnbind(@RequestBody body: Map<String, String>): ResponseEntity<Any> {
        val bindingUid = body["bindingUid"]
            ?: return ResponseEntity.badRequest().build()

        pushDaBindingRepository.deleteByBindingUid(bindingUid)
        return ResponseEntity.ok().build()
    }
}
