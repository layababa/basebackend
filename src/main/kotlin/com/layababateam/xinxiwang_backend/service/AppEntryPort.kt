package com.layababateam.xinxiwang_backend.service

data class SdkAppEntryToggleRequest(
    val enabled: Boolean,
)

data class SdkH5TicketRequest(
    val entryKey: String,
    val node: String? = null,
)

data class SdkH5SessionExchangeRequest(
    val ticket: String,
)

data class SdkAppEntryRequest(
    var entryKey: String = "",
    var placement: String = "",
    var titleZh: String = "",
    var titleEn: String = "",
    var icon: String = "book-open",
    var iconBgColor: String? = null,
    var iconColor: String? = null,
    var h5Url: String = "",
    var enabled: Boolean = false,
    var sort: Int = 0,
    var platforms: List<String>? = null,
    var minVersion: String? = null,
    var needLogin: Boolean = true,
    var badgeText: String? = null,
)

interface AppEntryPort {
    fun listAdminEntries(placement: String?, enabled: Boolean?): List<Any>

    fun saveEntry(input: SdkAppEntryRequest, id: String?, updatedBy: String): Any

    fun toggleEntry(id: String, enabled: Boolean, updatedBy: String): Any

    fun deleteEntry(id: String)

    fun listOpenEntries(placement: String, platform: String, version: String?): List<Any>

    fun createH5Ticket(
        userId: String,
        entryKey: String,
        platform: String?,
        clientVersion: String?,
        node: String?,
    ): Any

    fun exchangeH5Ticket(ticket: String): Any?
}
