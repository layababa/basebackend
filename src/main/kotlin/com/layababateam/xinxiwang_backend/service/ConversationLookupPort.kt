package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.Conversation

interface ConversationLookupPort {
    fun getConversation(convId: String): Conversation?
}
