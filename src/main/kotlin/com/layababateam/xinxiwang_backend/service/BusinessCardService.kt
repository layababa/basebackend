package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.CreateBusinessCardRequest
import com.layababateam.xinxiwang_backend.dto.UpdateBusinessCardRequest
import com.layababateam.xinxiwang_backend.model.BusinessCard
import com.layababateam.xinxiwang_backend.repository.BusinessCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class BusinessCardService(
    private val businessCardRepository: BusinessCardRepository
) {
    fun createCard(userId: String, request: CreateBusinessCardRequest): BusinessCard {
        val existingCards = businessCardRepository.findByUserId(userId)
        val isDefault = request.isDefault || existingCards.isEmpty()

        if (isDefault) {
            resetDefaultCard(userId)
        }

        val card = BusinessCard(
            userId = userId,
            name = request.name,
            title = request.title,
            company = request.company,
            phone = request.phone,
            email = request.email,
            address = request.address,
            avatarUrl = request.avatarUrl,
            website = request.website,
            customFields = request.customFields,
            isDefault = isDefault
        )
        return businessCardRepository.save(card)
    }

    fun updateCard(userId: String, cardId: String, request: UpdateBusinessCardRequest): BusinessCard {
        val existing = businessCardRepository.findById(cardId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "名片不存在") }

        if (existing.userId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "这不是您的名片")
        }

        if (request.isDefault == true && !existing.isDefault) {
            resetDefaultCard(userId)
        }

        val updated = existing.copy(
            name = request.name ?: existing.name,
            title = request.title ?: existing.title,
            company = request.company ?: existing.company,
            phone = request.phone ?: existing.phone,
            email = request.email ?: existing.email,
            address = request.address ?: existing.address,
            avatarUrl = request.avatarUrl ?: existing.avatarUrl,
            website = request.website ?: existing.website,
            customFields = request.customFields ?: existing.customFields,
            isDefault = request.isDefault ?: existing.isDefault,
            updatedAt = Instant.now()
        )
        return businessCardRepository.save(updated)
    }

    fun getCardsByUserId(userId: String): List<BusinessCard> {
        return businessCardRepository.findByUserId(userId)
    }

    fun getCardById(cardId: String): BusinessCard {
        return businessCardRepository.findById(cardId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "名片不存在") }
    }

    fun deleteCard(userId: String, cardId: String) {
        val existing = getCardById(cardId)
        if (existing.userId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "这不是您的名片")
        }
        businessCardRepository.deleteById(cardId)
    }

    private fun resetDefaultCard(userId: String) {
        val defaultCard = businessCardRepository.findByUserIdAndIsDefaultTrue(userId)
        if (defaultCard != null) {
            businessCardRepository.save(defaultCard.copy(isDefault = false, updatedAt = Instant.now()))
        }
    }
}
