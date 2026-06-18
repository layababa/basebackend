package com.layababateam.xinxiwang_backend.service

interface LoginSecurityMaintenancePort {
    fun refreshActiveBlocks()

    fun cleanupOldEvents()
}
