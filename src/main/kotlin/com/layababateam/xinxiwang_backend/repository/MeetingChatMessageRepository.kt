package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.MeetingChatMessage
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MeetingChatMessageRepository : MongoRepository<MeetingChatMessage, String> {
    fun findByMeetingIdOrderByCreatedAtAsc(meetingId: String): List<MeetingChatMessage>
    fun deleteByMeetingId(meetingId: String)
}
