package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.User

interface UserLookupPort {
    fun getUser(userId: String): User?
}
