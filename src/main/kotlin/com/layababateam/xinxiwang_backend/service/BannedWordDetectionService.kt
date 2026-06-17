package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.BannedWordHit
import com.layababateam.xinxiwang_backend.repository.BannedWordHitRepository
import com.layababateam.xinxiwang_backend.repository.BannedWordRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class BannedWordDetectionService(
    private val bannedWordRepository: BannedWordRepository,
    private val bannedWordHitRepository: BannedWordHitRepository,
    private val userLookupPort: UserLookupPort,
    private val conversationLookupPort: ConversationLookupPort
) {
    private val log = LoggerFactory.getLogger(BannedWordDetectionService::class.java)

    @Async
    fun detectAndRecord(senderId: String, conversationId: String, content: String) {
        try {
            val allWords = bannedWordRepository.findAll()
            if (allWords.isEmpty()) return

            val lowerContent = content.lowercase()
            val matchedWords = allWords.filter { lowerContent.contains(it.word.lowercase()) }

            if (matchedWords.isEmpty()) return

            val sender = userLookupPort.getUser(senderId)
            val senderName = sender?.displayName ?: senderId
            val conversation = conversationLookupPort.getConversation(conversationId)
            val conversationType = conversation?.type ?: 0
            val targetName = when (conversationType) {
                1 -> conversation?.name
                0 -> {
                    val peerId = conversation?.members?.find { it != senderId }
                    peerId?.let { userLookupPort.getUser(it)?.displayName }
                }
                else -> null
            }

            val hits = matchedWords.map { bannedWord ->
                BannedWordHit(
                    senderId = senderId,
                    senderName = senderName,
                    conversationId = conversationId,
                    conversationType = conversationType,
                    targetName = targetName,
                    originalContent = content.take(500),
                    matchedWord = bannedWord.word,
                    action = "BLOCKED"
                )
            }
            bannedWordHitRepository.saveAll(hits)
            log.info("Banned word hit recorded: sender={}, matched={}", senderId, matchedWords.map { it.word })
        } catch (e: Exception) {
            log.error("Failed to detect banned words for sender={}, conv={}", senderId, conversationId, e)
        }
    }
}
