package com.layababateam.xinxiwang_backend.dto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDtoOperatorTest {
    @Test
    fun userDtoDefaultsOperatorFlagToFalseForBackwardCompatibleConstruction() {
        val dto = UserDto(
            id = "user-1",
            username = "000002",
            displayName = "000002",
            avatarUrl = "https://example.com/avatar.png",
            gender = 0,
            bio = "bio",
            myInviteCode = "T000002",
        )

        assertFalse(dto.isOperator)
    }

    @Test
    fun userDtoCanExposeOperatorFlag() {
        val dto = UserDto(
            id = "user-1",
            username = "000001",
            displayName = "000001",
            avatarUrl = "https://example.com/avatar.png",
            gender = 0,
            bio = "bio",
            myInviteCode = "T000001",
            isOperator = true,
        )

        assertTrue(dto.isOperator)
    }

    @Test
    fun userDtoExposesCustomerServiceFlagForAvatarRoleBadges() {
        assertTrue(UserDto::class.members.any { it.name == "isCustomerService" })
    }
}
