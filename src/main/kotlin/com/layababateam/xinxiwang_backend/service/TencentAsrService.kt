package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.tencentcloudapi.asr.v20190614.AsrClient
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionRequest
import com.tencentcloudapi.common.Credential
import com.tencentcloudapi.common.profile.ClientProfile
import com.tencentcloudapi.common.profile.HttpProfile
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TencentAsrService(
    @Value("\${tencent.asr.secret-id}") private val secretId: String,
    @Value("\${tencent.asr.secret-key}") private val secretKey: String,
    @Value("\${tencent.asr.region}") private val region: String
) : AsrPort {
    private val log = LoggerFactory.getLogger(TencentAsrService::class.java)

    private val client: AsrClient by lazy {
        val cred = Credential(secretId, secretKey)
        val httpProfile = HttpProfile().apply {
            endpoint = "asr.tencentcloudapi.com"
            connTimeout = 3
            readTimeout = 15
        }
        val clientProfile = ClientProfile().apply {
            this.httpProfile = httpProfile
        }
        AsrClient(cred, region, clientProfile)
    }

    @CircuitBreaker(name = "asr", fallbackMethod = "transcribeFallback")
    override fun transcribe(audioUrl: String, format: String): String {
        val req = SentenceRecognitionRequest().apply {
            engSerViceType = "16k_zh"
            sourceType = 0L
            voiceFormat = format
            url = audioUrl
        }

        val resp = client.SentenceRecognition(req)
        return resp.result ?: ""
    }

    @Suppress("unused")
    private fun transcribeFallback(audioUrl: String, format: String, ex: Throwable): String {
        log.error("ASR transcribe failed, circuit open", ex)
        throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "语音识别服务暂不可用")
    }
}
