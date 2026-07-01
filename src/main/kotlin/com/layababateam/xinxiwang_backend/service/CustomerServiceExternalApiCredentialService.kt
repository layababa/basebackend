package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.CustomerServiceExternalApiCredentialRequest
import com.layababateam.xinxiwang_backend.dto.CustomerServiceExternalApiCredentialResponse
import com.layababateam.xinxiwang_backend.dto.CustomerServiceExternalApiCredentialSecretResponse
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import com.layababateam.xinxiwang_backend.repository.CustomerServiceExternalApiCredentialRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class CustomerServiceExternalApiCredentialService(
    private val credentialRepository: CustomerServiceExternalApiCredentialRepository,
    private val qrCodeRepository: CustomerServiceQrCodeRepository,
) {
    fun listCredentials(): List<CustomerServiceExternalApiCredentialResponse> =
        credentialRepository.findAllByOrderByCreatedAtDesc().map(::toResponse)

    fun createCredential(
        request: CustomerServiceExternalApiCredentialRequest,
        adminId: String?,
    ): CustomerServiceExternalApiCredentialSecretResponse {
        requireQr(request.qrCodeId)
        val now = System.currentTimeMillis()
        val secret = randomSecret()
        val saved = credentialRepository.save(
            CustomerServiceExternalApiCredential(
                name = request.name.trim(),
                apiKey = randomApiKey(),
                apiSecret = secret,
                qrCodeId = request.qrCodeId.trim(),
                enabled = request.enabled,
                createdBy = adminId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return CustomerServiceExternalApiCredentialSecretResponse(toResponse(saved), secret)
    }

    fun updateCredential(
        id: String,
        request: CustomerServiceExternalApiCredentialRequest,
    ): CustomerServiceExternalApiCredentialResponse {
        requireQr(request.qrCodeId)
        val current = credentialRepository.findById(id).getOrNull()
            ?: throw NotFoundException("external api credential not found")
        val saved = credentialRepository.save(
            current.copy(
                name = request.name.trim(),
                qrCodeId = request.qrCodeId.trim(),
                enabled = request.enabled,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return toResponse(saved)
    }

    fun rotateSecret(id: String): CustomerServiceExternalApiCredentialSecretResponse {
        val current = credentialRepository.findById(id).getOrNull()
            ?: throw NotFoundException("external api credential not found")
        val secret = randomSecret()
        val saved = credentialRepository.save(
            current.copy(
                apiSecret = secret,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return CustomerServiceExternalApiCredentialSecretResponse(toResponse(saved), secret)
    }

    private fun requireQr(qrCodeId: String) {
        qrCodeRepository.findById(qrCodeId.trim()).getOrNull()
            ?: throw NotFoundException("customer service qr not found")
    }

    private fun toResponse(credential: CustomerServiceExternalApiCredential): CustomerServiceExternalApiCredentialResponse =
        CustomerServiceExternalApiCredentialResponse(
            id = credential.id.orEmpty(),
            name = credential.name,
            apiKey = credential.apiKey,
            qrCodeId = credential.qrCodeId,
            enabled = credential.enabled,
            createdBy = credential.createdBy,
            createdAt = credential.createdAt,
            updatedAt = credential.updatedAt,
        )

    private fun randomApiKey(): String =
        "csak_${UUID.randomUUID().toString().replace("-", "")}"

    private fun randomSecret(): String {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        val RANDOM = SecureRandom()
    }
}
