package com.layababateam.xinxiwang_backend.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/health")
    fun health() = mapOf("status" to "UP")

    @GetMapping("/ping")
    fun ping() = mapOf("ts" to System.currentTimeMillis())
}
