package com.layababateam.xinxiwang_backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RealNameSubmitRequest(
    @field:NotBlank(message = "姓名不能为空")
    @field:Size(max = 30, message = "姓名长度不能超过 30 个字符")
    val realName: String,

    @field:NotBlank(message = "身份证号不能为空")
    @field:Pattern(
        regexp = "^[0-9]{17}[0-9Xx]$",
        message = "身份证号格式不正确"
    )
    val idCardNumber: String,

    @field:NotBlank(message = "请上传身份证正面照片")
    @field:Size(max = 500, message = "身份证正面图片地址过长")
    val idCardFrontUrl: String,

    @field:NotBlank(message = "请上传身份证反面照片")
    @field:Size(max = 500, message = "身份证反面图片地址过长")
    val idCardBackUrl: String,

    @field:Size(max = 500, message = "手持身份证图片地址过长")
    val handheldIdCardUrl: String? = null
)

data class RejectRealNameRequest(
    @field:NotBlank(message = "驳回原因不能为空")
    @field:Size(max = 200, message = "驳回原因不能超过 200 个字符")
    val reason: String
)

data class UpdateLotteryPasswordFreeRequest(
    val enabled: Boolean
)
