package com.layababateam.xinxiwang_backend.model

enum class ContentType(val value: Int) {
    TEXT(0), IMAGE(1), VOICE(2), VIDEO(3), FILE(4), RED_PACKET(5), STICKER(8),
    SYSTEM(10), TRANSFER(11), CALL(12), BUSINESS_CARD(13), GROUP_CARD(14),
    MEETING(16), MARKDOWN(17), BROADCAST(18), GROUP_RELAY(19), GROUP_CHECKIN(20);

    companion object {
        fun fromValue(v: Int): ContentType? = entries.find { it.value == v }
    }
}

enum class JoinMode(val value: Int) {
    OPEN(0), MEMBER_INVITE(1), ADMIN_ONLY(2);

    companion object {
        fun fromValue(v: Int): JoinMode? = entries.find { it.value == v }
    }
}

enum class AddFriendMode(val value: Int) {
    ALL(0), OWNER_ONLY(1), ADMIN_ONLY(2), MEMBER_ONLY(3);

    companion object {
        fun fromValue(v: Int): AddFriendMode? = entries.find { it.value == v }
    }
}

enum class PublicState(val value: Int) {
    DEFAULT(0), PUBLIC(2), PUBLIC_TOP(3);

    companion object {
        fun fromValue(v: Int): PublicState? = entries.find { it.value == v }
    }
}

enum class Gender(val value: Int) {
    MALE(0), FEMALE(1), UNKNOWN(2);

    companion object {
        fun fromValue(v: Int): Gender? = entries.find { it.value == v }
    }
}

enum class ApplyStatus(val value: Int) {
    PENDING(0), APPROVED(1), REJECTED(2);

    companion object {
        fun fromValue(v: Int): ApplyStatus? = entries.find { it.value == v }
    }
}

enum class RedPacketType(val value: Int) {
    NORMAL(0), LUCKY(1), EXCLUSIVE(3);

    companion object {
        fun fromValue(v: Int): RedPacketType? = entries.find { it.value == v }
    }
}

enum class MeetingType(val value: Int) {
    NORMAL(0), LIVE(1);

    companion object {
        fun fromValue(v: Int): MeetingType? = entries.find { it.value == v }
    }
}

enum class MeetingStatus(val value: Int) {
    ACTIVE(0), ENDED(1);

    companion object {
        fun fromValue(v: Int): MeetingStatus? = entries.find { it.value == v }
    }
}
