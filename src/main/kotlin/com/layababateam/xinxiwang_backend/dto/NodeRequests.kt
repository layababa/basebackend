package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateNodeRequest(
    @field:NotBlank(message = "节点名称不能为空")
    @field:Size(max = 50, message = "节点名称不能超过50个字符")
    val name: String,

    @field:NotBlank(message = "API 服务地址不能为空")
    val appServerUrl: String,

    @field:NotBlank(message = "WebSocket 地址不能为空")
    val websocketUrl: String,

    @field:NotBlank(message = "基础地址不能为空")
    val baseUrl: String,

    @field:NotBlank(message = "地区不能为空")
    @field:Pattern(regexp = "china|international", message = "地区只能是 china 或 international")
    val region: String,

    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val ossPublicEndpoint: String? = null,
    val ossFailbackEndpoint: String? = null,
)

data class UpdateNodeRequest(
    @field:Size(max = 50, message = "节点名称不能超过50个字符")
    val name: String? = null,
    val appServerUrl: String? = null,
    val websocketUrl: String? = null,
    val baseUrl: String? = null,
    @field:Pattern(regexp = "china|international", message = "地区只能是 china 或 international")
    val region: String? = null,
    val enabled: Boolean? = null,
    val sortOrder: Int? = null,
    val ossPublicEndpoint: String? = null,
    val ossFailbackEndpoint: String? = null,
)
