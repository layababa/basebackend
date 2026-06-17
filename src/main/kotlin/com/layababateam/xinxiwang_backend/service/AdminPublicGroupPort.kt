package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.PublicGroupApply
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface AdminPublicGroupPort {
    fun listPublicGroupApplies(status: Int?, pageable: Pageable): Page<PublicGroupApply>

    fun acceptPublicGroupApply(applyId: String, adminId: String, adminUsername: String): PublicGroupApply

    fun rejectPublicGroupApply(applyId: String, adminId: String, adminUsername: String): PublicGroupApply

    fun topPublicGroup(groupId: String, adminId: String, adminUsername: String)

    fun cancelTopPublicGroup(groupId: String, adminId: String, adminUsername: String)

    fun closePublicGroupOperation(groupId: String, adminId: String, adminUsername: String)
}
