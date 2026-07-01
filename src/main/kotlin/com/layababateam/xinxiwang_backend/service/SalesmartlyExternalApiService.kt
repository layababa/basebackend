package com.layababateam.xinxiwang_backend.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.CustomerServiceAccount
import com.layababateam.xinxiwang_backend.model.CustomerServiceExternalApiCredential
import com.layababateam.xinxiwang_backend.model.User
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceContentType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceMessage
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSenderType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.repository.CustomerServiceAccountRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceExternalApiCredentialRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrBindingRepository
import com.layababateam.xinxiwang_backend.repository.CustomerServiceQrCodeRepository
import com.layababateam.xinxiwang_backend.repository.UserRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull

data class SalesmartlyExternalApiResponse(
    val code: Int,
    val data: Any? = emptyMap<String, Any?>(),
    val msg: String = SalesmartlyExternalApiMessages.message(code),
    @get:JsonProperty("request_id")
    val requestId: String = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().replace("-", "").take(12)}",
    @get:JsonIgnore
    val httpStatus: Int = 200,
)

@Service
class SalesmartlyExternalApiService(
    private val credentialRepository: CustomerServiceExternalApiCredentialRepository,
    private val accountRepository: CustomerServiceAccountRepository,
    private val qrCodeRepository: CustomerServiceQrCodeRepository,
    private val bindingRepository: CustomerServiceQrBindingRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: WebCustomerServiceSessionRepository,
    private val messageRepository: WebCustomerServiceMessageRepository,
    private val workbenchService: CustomerServiceWorkbenchService,
    private val objectMapper: JsonMapper,
    private val signature: SalesmartlyExternalApiSignature,
) {
    private val rateHits = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun handle(
        method: String,
        path: String,
        queryParams: Map<String, Any?> = emptyMap(),
        bodyParams: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): SalesmartlyExternalApiResponse {
        val normalizedMethod = method.uppercase()
        val route = ROUTES[normalizedMethod to path]
            ?: return response(400, mapOf("path" to path, "method" to normalizedMethod), httpStatus = 404)
        val params = normalizeParams(queryParams) + normalizeParams(bodyParams)
        val missing = route.required.filter { params[it]?.toString()?.trim().isNullOrBlank() }
        if (missing.isNotEmpty()) return response(400, mapOf("missing" to missing))

        val projectId = params["project_id"].toString()
        val credential = credentialRepository.findByApiKey(projectId)
            ?.takeIf { it.enabled }
            ?: return response(100)
        val providedSign = headers.entries
            .firstOrNull { it.key.equals(SalesmartlyExternalApiSignature.SIGNATURE_HEADER, ignoreCase = true) }
            ?.value
        if (!signature.matches(providedSign, credential.apiSecret, params)) {
            return response(102)
        }
        if (!allow(projectId, route)) {
            return response(103, mapOf("qps" to route.qps))
        }

        return try {
            val result = when (route.kind) {
                "member_list" -> 0 to listMembers(projectId, credential, params)
                "contact_list" -> 0 to listContacts(projectId, credential, params)
                "add_contact" -> addContact(projectId, credential, params)
                "update_contact" -> updateContact(credential, params)
                "session_list" -> 0 to listSessions(projectId, credential, params)
                "message_list" -> 0 to listMessages(projectId, credential, params, requireChatUser = true)
                "all_message_list" -> 0 to listMessages(projectId, credential, params, requireChatUser = false)
                "assign_session" -> assignSession(credential, params)
                "end_session" -> endSession(credential, params)
                "tag_session" -> tagSession(credential, params)
                "unsupported_start" -> 207 to emptyMap<String, Any?>()
                "unsupported_purchase" -> 212 to emptyMap<String, Any?>()
                "unsupported_proxy" -> 215 to emptyMap<String, Any?>()
                "unsupported_remove" -> 214 to emptyMap<String, Any?>()
                else -> stub(route.kind, params)
            }
            response(result.first, result.second)
        } catch (e: SalesmartlyExternalApiFlowException) {
            response(e.code)
        } catch (_: NotFoundException) {
            response(211)
        } catch (_: ForbiddenException) {
            response(100)
        } catch (_: BusinessException) {
            response(400)
        } catch (_: Exception) {
            response(500)
        }
    }

    private fun listMembers(
        projectId: String,
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Map<String, Any?> {
        val rawTypes = params["type"]?.toString()?.trim().orEmpty()
        val allowedTypes = rawTypes
            .takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
        val items = agentsForCredential(credential)
            .mapIndexed { index, agent ->
                val now = seconds(agent.account.createdAt)
                val sysUserId = sysUserId(agent.user.id.orEmpty())
                mapOf(
                    "id" to (index + 1),
                    "project_id" to stableProjectInt(projectId),
                    "sys_user_id" to sysUserId,
                    "type" to 4,
                    "identity_id" to 1,
                    "online_status" to 1,
                    "assign_limit" to 50,
                    "online_time_switch" to 0,
                    "information_hidden" to 0,
                    "is_del" to 0,
                    "created_time" to now,
                    "updated_time" to seconds(agent.account.updatedAt),
                    "online_time_timezone" to "Asia/Singapore",
                    "online_time_configure" to "[]",
                    "nickname" to displayName(agent.account, agent.user),
                    "email" to "",
                    "avatar" to agent.user.avatarUrl,
                    "identity_name" to "Customer Service",
                    "groups" to listOf(mapOf("group_id" to 1, "group_name" to "Default")),
                )
            }
            .filter { allowedTypes == null || it["type"] in allowedTypes }
        return pageItems(items, params)
    }

    private fun listContacts(
        projectId: String,
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Map<String, Any?> {
        val sessions = sessionsForCredential(credential)
            .distinctBy { it.externalAnonymousId ?: it.visitorId }
        val filtered = sessions
            .map { contactRecord(projectId, it) }
            .filter { contact ->
                listOf("chat_user_id", "name", "email", "phone", "channel_uid").all { key ->
                    val raw = params[key]?.toString()?.trim().orEmpty()
                    raw.isBlank() || contact[key].toString().contains(raw, ignoreCase = true)
                }
            }
            .filter { contact ->
                listOf("channel", "channel_id", "sys_user_id", "is_online").all { key ->
                    val raw = params[key]?.toString()?.trim().orEmpty()
                    raw.isBlank() || contact[key].toString() == raw
                }
            }
            .filter { withinRange((it["updated_time"] as Number).toLong(), params["updated_time"]) }
            .filter { withinRange((it["created_time"] as Number).toLong(), params["created_time"]) }
            .sortedByDescending { (it["updated_time"] as Number).toLong() }
        return pageItems(filtered, params)
    }

    private fun addContact(
        projectId: String,
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Pair<Int, Map<String, Any?>> {
        val channel = intParam(params["channel"])
        if (channel !in setOf(3, 7, 12)) return 201 to emptyMap()
        val customerServiceUserId = selectCustomerServiceUserId(credential)
        val chatUserId = UUID.randomUUID().toString().replace("-", "").take(24)
        val now = System.currentTimeMillis()
        val name = firstNotBlank(
            params["remark_name"],
            params["email"],
            params["phone"],
            params["from_channel_info"],
            chatUserId,
        )
        val message = workbenchService.recordExternalApiVisitorMessage(
            customerServiceUserId = customerServiceUserId,
            externalApiCredentialId = credential.id.orEmpty(),
            externalAnonymousId = chatUserId,
            visitorName = name,
            sourceUrl = "salesmartly://${params["from_channel_info"]?.toString().orEmpty()}",
            content = "Conversation created.",
        )
        messageRepository.save(
            message.copy(
                senderType = WebCustomerServiceSenderType.SYSTEM,
                senderId = null,
                senderName = "system",
            ),
        )
        val session = sessionRepository.findById(message.sessionId).getOrNull()
            ?: throw SalesmartlyExternalApiFlowException(209)
        val saved = sessionRepository.save(
            session.copy(
                visitorName = name,
                visitorEmail = trimToNull(params["email"]),
                visitorPhone = trimToNull(params["phone"]),
                salesmartlyChannel = channel,
                salesmartlyChannelUid = trimToNull(params["from_channel_info"]).orEmpty(),
                salesmartlyRemark = trimToNull(params["remark"]),
                updatedAt = now,
            ),
        )
        return 0 to contactRecord(projectId, saved).filterKeys {
            it in setOf("channel", "channel_id", "channel_uid", "chat_user_id", "created_time", "email", "phone", "remark", "remark_name", "updated_time")
        }
    }

    private fun updateContact(
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Pair<Int, Map<String, Any?>> {
        val chatUserId = params["chat_user_id"].toString()
        val session = sessionsForCredential(credential).firstOrNull { chatUserIdFor(it) == chatUserId }
            ?: return 205 to emptyMap()
        val updated = sessionRepository.save(
            session.copy(
                visitorName = trimToNull(params["remark_name"]) ?: session.visitorName,
                visitorPhone = trimToNull(params["phone"]) ?: session.visitorPhone,
                visitorEmail = trimToNull(params["email"]) ?: session.visitorEmail,
                salesmartlyRemark = trimToNull(params["remark"]) ?: session.salesmartlyRemark,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return 0 to contactRecord(credential.apiKey, updated)
    }

    private fun listSessions(
        projectId: String,
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Map<String, Any?> {
        val status = intParam(params["session_status"], 0)
        val filtered = sessionsForCredential(credential)
            .filter { sessionStatus(it) == status }
            .filter {
                val subStatus = params["sub_status"]?.toString()?.takeIf { raw -> raw.isNotBlank() }?.toIntOrNull()
                subStatus == null || sessionSubStatus(it) == subStatus
            }
            .filter {
                val sysUser = params["sys_user_id"]?.toString()?.takeIf { raw -> raw.isNotBlank() }?.toIntOrNull()
                sysUser == null || sessionSysUserId(it) == sysUser
            }
            .filter {
                val sessionId = params["session_id"]?.toString()?.trim().orEmpty()
                sessionId.isBlank() || it.id == sessionId
            }
            .filter { withinRange(seconds(it.createdAt), params["start_time"]) }
            .filter { withinRange(seconds(it.closedAt ?: 0L), params["end_time"]) }
            .sortedByDescending { it.createdAt }
            .map { sessionRecord(projectId, it) }
        return pageItems(filtered, params)
    }

    private fun listMessages(
        projectId: String,
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
        requireChatUser: Boolean,
    ): Map<String, Any?> {
        var sessions = sessionsForCredential(credential)
        if (requireChatUser) {
            val chatUserId = params["chat_user_id"].toString()
            sessions = sessions.filter { chatUserIdFor(it) == chatUserId }
        }
        val sessionId = trimToNull(params["session_id"])
        if (sessionId != null) sessions = sessions.filter { it.id == sessionId }
        val messages = sessions
            .flatMap { session ->
                messageRepository
                    .findBySessionIdOrderByCreatedAtAsc(session.id.orEmpty(), PageRequest.of(0, 1000))
                    .map { messageRecord(projectId, session, it) }
            }
            .filter {
                val raw = params["msg_content"]?.toString()?.trim().orEmpty()
                raw.isBlank() || it["text"].toString().contains(raw, ignoreCase = true)
            }
            .filter {
                val start = params["start_sequence_id"]?.toString()?.toLongOrNull()
                start == null || (it["sequence_id"] as Number).toLong() >= start
            }
            .filter {
                val end = params["end_sequence_id"]?.toString()?.toLongOrNull()
                end == null || (it["sequence_id"] as Number).toLong() <= end
            }
            .filter { withinRange((it["updated_time"] as Number).toLong(), params["updated_time"]) }
            .sortedBy { (it["sequence_id"] as Number).toLong() }
        val paged = pageItems(messages, params)
        return mapOf(
            "list" to paged["list"],
            "page_size" to paged["page_size"],
            "total" to paged["total"],
        )
    }

    private fun assignSession(
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Pair<Int, Map<String, Any?>> {
        val session = ownedSession(credential, params["session_id"].toString()) ?: return 209 to emptyMap()
        if (chatUserIdFor(session) != params["chat_user_id"].toString()) return 209 to emptyMap()
        val assignSysUserId = intParam(params["assign_sys_user_id"])
        val agent = if (assignSysUserId <= 0) null else agentsForCredential(credential)
            .firstOrNull { sysUserId(it.user.id.orEmpty()) == assignSysUserId }
            ?: return 210 to emptyMap()
        sessionRepository.save(
            session.copy(
                status = if (agent == null) WebCustomerServiceSessionStatus.WAITING else WebCustomerServiceSessionStatus.ACTIVE,
                assignedAdminId = agent?.user?.id,
                assignedAdminUsername = agent?.let { displayName(it.account, it.user) },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return 0 to emptyMap()
    }

    private fun endSession(
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Pair<Int, Map<String, Any?>> {
        val session = ownedSession(credential, params["session_id"].toString()) ?: return 209 to emptyMap()
        if (chatUserIdFor(session) != params["chat_user_id"].toString()) return 209 to emptyMap()
        val now = System.currentTimeMillis()
        sessionRepository.save(
            session.copy(
                status = WebCustomerServiceSessionStatus.CLOSED,
                closedAt = now,
                updatedAt = now,
            ),
        )
        return 0 to emptyMap()
    }

    private fun tagSession(
        credential: CustomerServiceExternalApiCredential,
        params: Map<String, Any?>,
    ): Pair<Int, Map<String, Any?>> {
        val session = ownedSession(credential, params["session_id"].toString()) ?: return 209 to emptyMap()
        val incoming = (params["tag_ids"] ?: params["session_tag_ids"])
            ?.toString()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val existing = tagIds(session.salesmartlyTagsJson)
        val result = when (params["operation"]?.toString()?.lowercase()) {
            "replace", "set", "2" -> incoming
            "delete", "remove", "3" -> existing.filterNot { it in incoming.toSet() }
            else -> (existing + incoming).distinct()
        }
        sessionRepository.save(session.copy(salesmartlyTagsJson = tagsJson(result), updatedAt = System.currentTimeMillis()))
        return 0 to emptyMap()
    }

    private fun stub(kind: String, params: Map<String, Any?>): Pair<Int, Map<String, Any?>> =
        when (kind) {
            "page" -> 0 to pageItems(emptyList(), params)
            "id" -> 0 to mapOf("id" to UUID.randomUUID().toString().replace("-", "").take(12))
            "object" -> 0 to emptyMap()
            "async" -> 0 to mapOf("res" to 1, "session_status" to "queued")
            else -> 0 to emptyMap()
        }

    private fun contactRecord(projectId: String, session: WebCustomerServiceSession): Map<String, Any?> {
        val phone = session.visitorPhone.orEmpty()
        return mapOf(
            "chat_user_id" to chatUserIdFor(session),
            "name" to (session.visitorName ?: chatUserIdFor(session)),
            "remark_name" to session.visitorName.orEmpty(),
            "remark" to session.salesmartlyRemark.orEmpty(),
            "email" to session.visitorEmail.orEmpty(),
            "phone" to phone,
            "phone_number" to phone.removePrefix("+"),
            "area_code" to "",
            "channel" to session.salesmartlyChannel,
            "channel_id" to 0,
            "channel_uid" to session.salesmartlyChannelUid,
            "channel_info" to "",
            "project_id" to stableProjectInt(projectId),
            "sys_user_id" to sessionSysUserId(session),
            "country" to "",
            "city" to "",
            "is_online" to 0,
            "language" to "",
            "labels" to "[]",
            "custom_field" to emptyList<Any?>(),
            "is_del" to 0,
            "created_time" to seconds(session.createdAt),
            "updated_time" to seconds(session.updatedAt),
        )
    }

    private fun sessionRecord(projectId: String, session: WebCustomerServiceSession): Map<String, Any?> {
        val messages = messagesForSession(session)
        return mapOf(
            "chat_user_id" to chatUserIdFor(session),
            "session_id" to session.id.orEmpty(),
            "channel" to session.salesmartlyChannel,
            "channel_id" to 0,
            "title" to (session.visitorName ?: chatUserIdFor(session)),
            "start_time" to seconds(session.createdAt),
            "end_time" to if (session.closedAt == null) 0 else seconds(session.closedAt),
            "assign_time" to seconds(session.updatedAt),
            "score" to 0,
            "quality_score" to 0,
            "sys_user_id" to sessionSysUserId(session),
            "tags" to session.salesmartlyTagsJson,
            "msg_count" to messages.size,
            "user_msg_count" to messages.count { it.senderType == WebCustomerServiceSenderType.VISITOR },
            "customer_msg_count" to messages.count { it.senderType == WebCustomerServiceSenderType.ADMIN },
            "assign_source" to "",
            "project_id" to stableProjectInt(projectId),
        )
    }

    private fun messageRecord(
        projectId: String,
        session: WebCustomerServiceSession,
        message: WebCustomerServiceMessage,
    ): Map<String, Any?> {
        val seconds = seconds(message.createdAt)
        return mapOf(
            "chat_user_id" to chatUserIdFor(session),
            "sequence_id" to message.createdAt * 1000L,
            "is_system" to if (message.senderType == WebCustomerServiceSenderType.SYSTEM) 1 else 0,
            "send_time" to seconds,
            "read_time" to 0,
            "project_id" to stableProjectInt(projectId),
            "sender" to (message.senderName ?: message.senderId ?: ""),
            "reader" to "",
            "sender_type" to when (message.senderType) {
                WebCustomerServiceSenderType.VISITOR -> 1
                WebCustomerServiceSenderType.ADMIN -> 2
                WebCustomerServiceSenderType.SYSTEM -> 3
            },
            "mid" to message.id.orEmpty(),
            "msg_type" to when (message.contentType) {
                WebCustomerServiceContentType.IMAGE -> 2
                WebCustomerServiceContentType.TEXT -> if (message.senderType == WebCustomerServiceSenderType.SYSTEM) 8 else 1
            },
            "text" to if (message.contentType == WebCustomerServiceContentType.IMAGE) message.imageUrl.orEmpty() else message.content,
            "chat_session_id" to session.id.orEmpty(),
            "session_id" to session.id.orEmpty(),
            "is_reply" to 0,
            "is_withdraw" to 0,
            "updated_time" to seconds,
        )
    }

    private fun selectCustomerServiceUserId(credential: CustomerServiceExternalApiCredential): String {
        val agents = agentsForCredential(credential)
        return agents.firstOrNull()?.user?.id?.takeIf { it.isNotBlank() }
            ?: throw SalesmartlyExternalApiFlowException(210)
    }

    private fun agentsForCredential(credential: CustomerServiceExternalApiCredential): List<SalesmartlyAgent> {
        val qr = qrCodeRepository.findById(credential.qrCodeId).getOrNull()
            ?: throw SalesmartlyExternalApiFlowException(211)
        if (!qr.enabled) throw SalesmartlyExternalApiFlowException(211)
        val bindings = bindingRepository
            .findByQrCodeIdOrderByAssignedCountAscSortOrderAscCreatedAtAsc(qr.id.orEmpty())
            .filter { it.enabled }
        val accounts = accountRepository.findAllById(bindings.map { it.customerServiceId })
            .filter { it.enabled }
            .associateBy { it.id.orEmpty() }
        val users = userRepository.findAllById(accounts.values.map { it.userId })
            .filter { !it.isDeleted && it.isOperator }
            .associateBy { it.id.orEmpty() }
        return bindings.mapNotNull { binding ->
            val account = accounts[binding.customerServiceId] ?: return@mapNotNull null
            val user = users[account.userId] ?: return@mapNotNull null
            SalesmartlyAgent(account, user)
        }
    }

    private fun sessionsForCredential(credential: CustomerServiceExternalApiCredential): List<WebCustomerServiceSession> =
        sessionRepository.findByExternalApiCredentialIdOrderByLastMessageAtDesc(credential.id.orEmpty())

    private fun ownedSession(credential: CustomerServiceExternalApiCredential, sessionId: String): WebCustomerServiceSession? =
        sessionRepository.findById(sessionId).getOrNull()
            ?.takeIf { it.externalApiCredentialId == credential.id.orEmpty() }

    private fun messagesForSession(session: WebCustomerServiceSession): List<WebCustomerServiceMessage> =
        messageRepository.findBySessionIdOrderByCreatedAtAsc(session.id.orEmpty(), PageRequest.of(0, 1000))

    private fun allow(projectId: String, route: SalesmartlyRoute): Boolean {
        val key = "$projectId:${route.method}:${route.path}"
        val now = System.currentTimeMillis()
        val hits = rateHits.computeIfAbsent(key) { ArrayDeque() }
        synchronized(hits) {
            while (hits.isNotEmpty() && now - hits.first() >= 1000L) {
                hits.removeFirst()
            }
            if (hits.size >= route.qps) return false
            hits.addLast(now)
            return true
        }
    }

    private fun response(
        code: Int,
        data: Any? = emptyMap<String, Any?>(),
        httpStatus: Int = 200,
    ): SalesmartlyExternalApiResponse =
        SalesmartlyExternalApiResponse(code = code, data = data, httpStatus = httpStatus)

    private fun pageItems(items: List<Map<String, Any?>>, params: Map<String, Any?>): Map<String, Any?> {
        val page = intParam(params["page"], 1).coerceAtLeast(1)
        val pageSize = intParam(params["page_size"], 20).coerceIn(1, 100)
        val start = (page - 1) * pageSize
        return mapOf(
            "list" to items.drop(start).take(pageSize),
            "page" to page,
            "page_size" to pageSize,
            "total" to items.size,
        )
    }

    private fun normalizeParams(params: Map<String, Any?>): Map<String, Any?> =
        params.mapValues { (_, value) ->
            when (value) {
                null -> ""
                is Array<*> -> value.joinToString(",") { it?.toString().orEmpty() }
                is Iterable<*> -> value.joinToString(",") { it?.toString().orEmpty() }
                else -> value
            }
        }

    private fun withinRange(value: Long, rawRange: Any?): Boolean {
        val raw = rawRange?.toString()?.takeIf { it.isNotBlank() } ?: return true
        val start = Regex(""""start"\s*:\s*(\d+)""").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val end = Regex(""""end"\s*:\s*(\d+)""").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
        return (start == null || value >= start) && (end == null || value <= end)
    }

    private fun stableProjectInt(projectId: String): Int =
        stableInt(projectId)

    private fun sysUserId(userId: String): Int =
        stableInt("sys:$userId")

    private fun stableInt(value: String): Int {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        val raw = ((digest[0].toInt() and 0xff) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)
        return (raw and Int.MAX_VALUE).takeIf { it > 0 } ?: 1
    }

    private fun chatUserIdFor(session: WebCustomerServiceSession): String =
        session.externalAnonymousId ?: session.visitorId

    private fun sessionStatus(session: WebCustomerServiceSession): Int =
        if (session.status == WebCustomerServiceSessionStatus.CLOSED) 1 else 0

    private fun sessionSubStatus(session: WebCustomerServiceSession): Int =
        if (session.assignedAdminId.isNullOrBlank()) 1 else 0

    private fun sessionSysUserId(session: WebCustomerServiceSession): Int =
        session.assignedAdminId?.takeIf { it.isNotBlank() }?.let(::sysUserId) ?: 0

    private fun displayName(account: CustomerServiceAccount, user: User): String =
        trimToNull(account.displayName) ?: trimToNull(user.displayName) ?: user.username

    private fun seconds(value: Long?): Long =
        if (value == null || value <= 0L) 0L else value / 1000L

    private fun intParam(value: Any?, default: Int = 0): Int =
        value?.toString()?.toIntOrNull() ?: default

    private fun trimToNull(value: Any?): String? =
        value?.toString()?.trim()?.takeIf { it.isNotBlank() }

    private fun firstNotBlank(vararg values: Any?): String =
        values.firstNotNullOfOrNull(::trimToNull).orEmpty()

    private fun tagIds(raw: String): List<String> =
        Regex(""""tag_id"\s*:\s*"([^"]+)"""")
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()

    private fun tagsJson(ids: List<String>): String =
        ids.joinToString(prefix = "[", postfix = "]") { id ->
            val escaped = escapeJson(id)
            """{"tag_id":"$escaped","tag_name":"$escaped","children":[]}"""
        }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private data class SalesmartlyAgent(
        val account: CustomerServiceAccount,
        val user: User,
    )

    private data class SalesmartlyRoute(
        val priority: String,
        val method: String,
        val path: String,
        val kind: String,
        val qps: Int,
        val required: List<String>,
    )

    private class SalesmartlyExternalApiFlowException(val code: Int) : RuntimeException()

    companion object {
        private const val READ_QPS = 10
        private const val WRITE_QPS = 20

        private val ROUTES = listOf(
            route("P2", "GET", "/api/v2/get-label-list", "page"),
            route("P2", "GET", "/api/v2/visitor-label/categories", "page"),
            route("P2", "POST", "/api/v2/visitor-label/category/create", "id"),
            route("P2", "POST", "/api/v2/visitor-label/category/update", "empty"),
            route("P2", "POST", "/api/v2/visitor-label/category/delete", "empty"),
            route("P2", "GET", "/api/v2/visitor-label/labels", "page"),
            route("P2", "GET", "/api/v2/visitor-label/label", "object"),
            route("P2", "POST", "/api/v2/visitor-label/label/create", "id"),
            route("P2", "POST", "/api/v2/visitor-label/label/update", "empty"),
            route("P2", "POST", "/api/v2/visitor-label/label/delete", "empty"),
            route("P2", "POST", "/api/v2/visitor-label/value/create", "id"),
            route("P2", "POST", "/api/v2/visitor-label/value/update", "empty"),
            route("P2", "POST", "/api/v2/visitor-label/value/delete", "empty"),
            route("P2", "GET", "/api/v2/get-custom-field-list", "page"),
            route("P2", "POST", "/api/v2/update-custom-field", "id"),
            route("P2", "POST", "/api/v2/enable-custom-field", "empty"),
            route("P2", "POST", "/api/v2/del-custom-field", "empty"),
            route("P2", "POST", "/api/v2/update-custom-field-sort", "empty"),
            route("P1", "GET", "/api/v2/get-contact-list", "contact_list", 10),
            route("P1", "POST", "/api/v2/add-contact", "add_contact", 20),
            route("P2", "POST", "/api/v2/batch-update-label", "async", 20),
            route("P1", "POST", "/api/v2/update-user-info", "update_contact", 10),
            route("P2", "POST", "/api/v2/add-contact-order", "id", 20),
            route("P2", "POST", "/api/v2/batch-assign-contact", "async"),
            route("P1", "GET", "/api/v2/get-member-list", "member_list", 10),
            route("P3", "GET", "/api/v2/get-session-tag-category-list", "page"),
            route("P3", "POST", "/api/v2/add-session-tag-category", "id"),
            route("P3", "POST", "/api/v2/update-session-tag-category", "empty"),
            route("P3", "GET", "/api/v2/get-session-tag-list", "page"),
            route("P3", "POST", "/api/v2/delete-session-tag-category", "empty"),
            route("P3", "POST", "/api/v2/add-session-tag", "id"),
            route("P3", "POST", "/api/v2/update-session-tag", "empty"),
            route("P3", "POST", "/api/v2/delete-session-tag", "empty"),
            route("P3", "POST", "/api/v2/tag-session", "tag_session"),
            route("P1", "GET", "/api/v2/get-message-list", "message_list", 10),
            route("P1", "GET", "/api/v2/get-all-message-list", "all_message_list", 10),
            route("P1", "POST", "/api/v2/assign-chat-user", "assign_session", 20),
            route("P1", "POST", "/api/v2/end-session", "end_session", 20),
            route("P3", "POST", "/api/v2/batch-talk-back", "async"),
            route("P1", "GET", "/api/v2/get-session-list", "session_list"),
            route("P4", "GET", "/api/v2/get-link-list", "page", 10),
            route("P4", "GET", "/api/v2/get-link-record-list", "page", 10),
            route("P5", "GET", "/api/v2/get-individual-whatsapp-list", "page", 20),
            route("P5", "GET", "/api/v2/start-individual-whatsapp-app", "unsupported_start", 30),
            route("P5", "POST", "/api/v2/purchase-individual-whatsapp-app", "unsupported_purchase", 10),
            route("P5", "POST", "/api/v2/set-individual-whatsapp-app-proxy", "unsupported_proxy", 20),
            route("P5", "POST", "/api/v2/remove-individual-whatsapp-app", "unsupported_remove", 10),
            route("P5", "GET", "/api/v2/get-whatsapp-api-list", "page"),
            route("P5", "GET", "/api/v2/get-messenger-list", "page"),
            route("P5", "GET", "/api/v2/get-line-list", "page"),
            route("P5", "GET", "/api/v2/get-line-app-list", "page"),
            route("P4", "GET", "/api/v2/get-online-duration-report", "page"),
            route("P4", "GET", "/api/v2/get-big-report", "page"),
            route("P4", "GET", "/api/v2/get-customer-analysis-report", "page"),
        ).associateBy { it.method to it.path }

        private fun route(
            priority: String,
            method: String,
            path: String,
            kind: String,
            qps: Int? = null,
        ): SalesmartlyRoute =
            SalesmartlyRoute(
                priority = priority,
                method = method,
                path = path,
                kind = kind,
                qps = qps ?: if (method == "GET") READ_QPS else WRITE_QPS,
                required = when (kind) {
                    "member_list" -> listOf("project_id", "page", "page_size")
                    "contact_list" -> listOf("project_id", "updated_time", "page", "page_size")
                    "add_contact" -> listOf("project_id", "channel", "from_channel_info")
                    "update_contact" -> listOf("project_id", "chat_user_id")
                    "session_list" -> listOf("project_id", "page", "page_size")
                    "message_list" -> listOf("project_id", "page_size", "chat_user_id")
                    "all_message_list" -> listOf("project_id", "page", "page_size")
                    "assign_session" -> listOf("project_id", "session_id", "chat_user_id", "sys_user_id", "assign_sys_user_id")
                    "end_session" -> listOf("project_id", "session_id", "chat_user_id")
                    "tag_session" -> listOf("project_id", "session_id")
                    else -> listOf("project_id")
                },
            )
    }
}

private object SalesmartlyExternalApiMessages {
    private val messages = mapOf(
        0 to "success",
        100 to "Invalid API Token",
        101 to "Invalid Bearer Token",
        102 to "Invalid External Sign",
        103 to "API Frequency Limit",
        201 to "Channel Not Exist",
        202 to "Channel Info Not Exist",
        203 to "Channel User Exist",
        204 to "Create Contact Failed",
        205 to "Update Contact Failed",
        206 to "Chat Order Exist",
        207 to "Individual Whatsapp Can't Start",
        209 to "Chat Session Not Exist",
        210 to "Sys User Not Exist",
        211 to "Split Link Not Exist",
        212 to "Project Consume Not Enough",
        213 to "Whatsapp Server Not Enough",
        214 to "Individual Whatsapp Not Exist",
        215 to "Proxy Config Error",
        216 to "Redis Lock Error",
        217 to "Is Operation Now",
        218 to "Channel User Not Exist",
        400 to "Invalid Params",
        500 to "Database Error",
        600 to "Create User Error",
        601 to "Create Login Code Error",
    )

    fun message(code: Int): String = messages[code] ?: "Unknown Error"
}
