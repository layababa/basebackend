package com.layababateam.xinxiwang_backend.service

/**
 * 群活动 WebSocket 能力契约。
 *
 * SDK 负责接龙/签到协议入口和响应格式，业务侧负责活动规则、落库、积分和广播。
 */
interface GroupActivityPort {
    fun getRelay(userId: String, relayId: String): GroupActivityQueryResult<Map<String, Any?>>

    fun listRelays(userId: String, conversationId: String): GroupActivityQueryResult<List<Map<String, Any?>>>

    fun getCheckin(userId: String, checkinId: String): GroupActivityQueryResult<Map<String, Any?>>

    fun listCheckins(userId: String, conversationId: String): GroupActivityQueryResult<List<Map<String, Any?>>>

    fun createRelay(operatorId: String, conversationId: String, title: String, description: String?): Map<String, Any?>

    fun addRelayEntry(operatorId: String, relayId: String, content: String): Map<String, Any?>

    fun closeRelay(operatorId: String, relayId: String): Map<String, Any?>

    fun createCheckin(operatorId: String, conversationId: String, title: String, description: String?): Map<String, Any?>

    fun signCheckin(operatorId: String, checkinId: String, content: String?): CheckinSignResult

    fun closeCheckin(operatorId: String, checkinId: String): Map<String, Any?>
}

data class CheckinSignResult(
    val data: Map<String, Any?>,
    val awarded: Map<String, Any?>?,
)

data class GroupActivityQueryResult<T>(
    val data: T? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun <T> ok(data: T): GroupActivityQueryResult<T> = GroupActivityQueryResult(data = data)

        fun <T> error(message: String): GroupActivityQueryResult<T> = GroupActivityQueryResult(errorMessage = message)
    }
}
