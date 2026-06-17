package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.model.BannedWord
import com.layababateam.xinxiwang_backend.model.BannedWordHit
import com.layababateam.xinxiwang_backend.model.SystemConfig

/**
 * 后台系统配置与违禁词管理端口。
 *
 * SDK 复用后台 HTTP 契约、参数校验和审计动作，配置缓存与仓库读写由接入方实现。
 */
interface AdminSystemPort {
    fun listSystemConfig(): List<SystemConfig>
    fun saveSystemConfig(values: Map<String, String>): List<SystemConfig>
    fun getAssetSwitches(): Map<String, Boolean>
    fun saveAssetSwitches(values: Map<String, Boolean>): Map<String, Boolean>
    fun listBannedWords(page: Int, size: Int): PagedData<BannedWord>
    fun bannedWordExists(word: String): Boolean
    fun addBannedWord(word: String, adminId: String): BannedWord
    fun findBannedWord(id: String): BannedWord?
    fun deleteBannedWord(id: String)
    fun existingBannedWords(): Set<String>
    fun addBannedWords(words: List<String>, adminId: String): List<BannedWord>
    fun findBannedWordsByIds(ids: List<String>): List<BannedWord>
    fun deleteBannedWords(ids: List<String>)
    fun listBannedWordHits(page: Int, size: Int, keyword: String?): PagedData<BannedWordHit>
    fun bannedWordHitExists(id: String): Boolean
    fun deleteBannedWordHit(id: String)
    fun countBannedWordHits(): Long
    fun clearBannedWordHits()
}
