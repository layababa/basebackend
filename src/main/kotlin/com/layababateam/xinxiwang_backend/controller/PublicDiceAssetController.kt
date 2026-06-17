package com.layababateam.xinxiwang_backend.controller

import com.layababateam.xinxiwang_backend.service.PublicDiceAssetService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/public/dice")
class PublicDiceAssetController(
    private val diceAssetService: PublicDiceAssetService,
) {
    @GetMapping("/{value}.png", produces = [MediaType.IMAGE_PNG_VALUE])
    fun dice(@PathVariable value: Int): ResponseEntity<ByteArray> {
        val bytes = diceAssetService.getDicePng(value) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .contentType(MediaType.IMAGE_PNG)
            .body(bytes)
    }
}
