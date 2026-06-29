package com.layababateam.xinxiwang_backend.service

import com.aliyuncs.DefaultAcsClient
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest
import com.aliyuncs.profile.DefaultProfile
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class EmailService(
    @Value("\${alibaba.email.accessKeyId}") private val accessKeyId: String,
    @Value("\${alibaba.email.accessKeySecret}") private val accessKeySecret: String,
    @Value("\${alibaba.email.accountName}") private val accountName: String,
    @Value("\${alibaba.email.fromAlias}") private val fromAlias: String,
    @Value("\${alibaba.email.regionId}") private val regionId: String,
) : VerificationEmailPort {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    private val client: DefaultAcsClient? by lazy {
        if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
            log.warn("[EMAIL] 阿里云 DirectMail 未配置")
            null
        } else {
            DefaultProfile.addEndpoint(regionId, "Dm", "dm.$regionId.aliyuncs.com")
            val profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret)
            DefaultAcsClient(profile)
        }
    }

    override fun send(to: String, subject: String, body: String) {
        val acsClient = client
        if (acsClient == null) {
            log.warn("[EMAIL] DirectMail 未配置，跳过发送: to={}, subject={}", to, subject)
            return
        }

        try {
            val request = SingleSendMailRequest()
            request.accountName = accountName
            request.fromAlias = fromAlias
            request.addressType = 1
            request.toAddress = to
            request.subject = subject
            request.textBody = body
            request.replyToAddress = false

            acsClient.getAcsResponse(request)
            log.info("[EMAIL] 邮件已发送: to={}, subject={}", to, subject)
        } catch (e: Exception) {
            log.error("[EMAIL] 邮件发送失败: to={}, error={}", to, e.message, e)
        }
    }
}
