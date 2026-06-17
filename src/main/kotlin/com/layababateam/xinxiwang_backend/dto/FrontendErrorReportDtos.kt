package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.Size

data class FrontendErrorReportRequest(
    @field:Size(max = 60, message = "事件类型不能超过 60 个字符")
    val eventType: String = "REGISTER_ERROR",
    @field:Size(max = 80, message = "页面名称不能超过 80 个字符")
    val screen: String? = null,
    @field:Size(max = 160, message = "接口地址不能超过 160 个字符")
    val endpoint: String? = null,
    @field:Size(max = 12, message = "请求方法不能超过 12 个字符")
    val method: String? = null,
    @field:Size(max = 60, message = "错误分类不能超过 60 个字符")
    val errorCategory: String? = null,
    @field:Size(max = 20, message = "严重程度不能超过 20 个字符")
    val severity: String? = null,
    @field:Size(max = 1000, message = "错误信息不能超过 1000 个字符")
    val errorMessage: String? = null,
    @field:Size(max = 80, message = "错误码不能超过 80 个字符")
    val errorCode: String? = null,
    val statusCode: Int? = null,
    val requestJson: Map<String, Any?>? = null,
    val registrationInfo: Map<String, Any?>? = null,
    val clientInfo: Map<String, Any?>? = null,
    val debugContext: Map<String, Any?>? = null
)

data class AdminUpdateFrontendErrorReportRequest(
    @field:Size(max = 20, message = "状态不能超过 20 个字符")
    val status: String,
    @field:Size(max = 1000, message = "备注不能超过 1000 个字符")
    val adminNote: String? = null
)
