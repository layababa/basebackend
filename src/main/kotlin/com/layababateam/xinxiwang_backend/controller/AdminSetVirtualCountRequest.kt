package com.layababateam.xinxiwang_backend.controller

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class AdminSetVirtualCountRequest(
    @field:Min(0)
    @field:Max(100)
    val count: Int,
)
