package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.dto.ApiResponse
import com.layababateam.xinxiwang_backend.dto.ErrorCode
import com.layababateam.xinxiwang_backend.dto.PagedData
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceBootstrapResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceCreateSessionRequest
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceCreateSessionResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceEntryRequest
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceEntryResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceMessageResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceMessagesResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceScriptResponse
import com.layababateam.xinxiwang_backend.dto.WebCustomerServiceSessionResponse
import com.layababateam.xinxiwang_backend.dto.toWebCustomerServiceResponse
import com.layababateam.xinxiwang_backend.exception.BusinessException
import com.layababateam.xinxiwang_backend.exception.ForbiddenException
import com.layababateam.xinxiwang_backend.exception.NotFoundException
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceContentType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceEntry
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceMessage
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSenderType
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSession
import com.layababateam.xinxiwang_backend.model.WebCustomerServiceSessionStatus
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceEntryRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceMessageRepository
import com.layababateam.xinxiwang_backend.repository.WebCustomerServiceSessionRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.jvm.optionals.getOrNull

@Service
class WebCustomerServiceService(
    private val entryRepository: WebCustomerServiceEntryRepository,
    private val sessionRepository: WebCustomerServiceSessionRepository,
    private val messageRepository: WebCustomerServiceMessageRepository,
    private val tokenService: WebCustomerServiceTokenService,
    private val uploadPort: UploadPort,
    private val mongoTemplate: MongoTemplate,
) {
    fun listEntries(): List<WebCustomerServiceEntryResponse> =
        entryRepository.findAllByOrderByCreatedAtDesc().map(::toEntryResponse)

    fun createEntry(request: WebCustomerServiceEntryRequest, adminId: String): WebCustomerServiceEntryResponse {
        val normalized = normalizeEntryRequest(request)
        val now = System.currentTimeMillis()
        val entry = entryRepository.save(
            WebCustomerServiceEntry(
                name = normalized.name.trim(),
                enabled = normalized.enabled,
                allowedDomains = normalized.allowedDomains,
                seatAdminIds = normalized.seatAdminIds,
                welcomeMessage = normalized.welcomeMessage.trim(),
                themeColor = WebCustomerServiceRules.normalizeThemeColor(normalized.themeColor),
                createdBy = adminId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return toEntryResponse(entry)
    }

    fun updateEntry(id: String, request: WebCustomerServiceEntryRequest): WebCustomerServiceEntryResponse {
        val current = entryRepository.findById(id).getOrNull() ?: throw NotFoundException("客服入口不存在")
        val normalized = normalizeEntryRequest(request)
        val updated = entryRepository.save(
            current.copy(
                name = normalized.name.trim(),
                enabled = normalized.enabled,
                allowedDomains = normalized.allowedDomains,
                seatAdminIds = normalized.seatAdminIds,
                welcomeMessage = normalized.welcomeMessage.trim(),
                themeColor = WebCustomerServiceRules.normalizeThemeColor(normalized.themeColor),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return toEntryResponse(updated)
    }

    fun script(id: String, request: HttpServletRequest): WebCustomerServiceScriptResponse {
        entryById(id)
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8)
        val scriptUrl = "${externalBaseUrl(request)}/api/web-customer-service/widget.js?entryId=$encoded"
        return WebCustomerServiceScriptResponse(
            scriptUrl = scriptUrl,
            scriptTag = """<script async src="$scriptUrl"></script>""",
        )
    }

    fun bootstrap(entryId: String, request: HttpServletRequest): WebCustomerServiceBootstrapResponse {
        val entry = publicEntry(entryId, request)
        return WebCustomerServiceBootstrapResponse(
            entryId = entry.id.orEmpty(),
            name = entry.name,
            enabled = entry.enabled,
            welcomeMessage = entry.welcomeMessage,
            themeColor = entry.themeColor,
        )
    }

    fun createSession(
        entryId: String,
        body: WebCustomerServiceCreateSessionRequest,
        request: HttpServletRequest,
    ): WebCustomerServiceCreateSessionResponse {
        val entry = publicEntry(entryId, request)
        val now = System.currentTimeMillis()
        val current = sessionRepository.findFirstByEntryIdAndVisitorIdAndStatusNotOrderByLastMessageAtDesc(
            entryId = entry.id.orEmpty(),
            visitorId = body.visitorId.trim(),
            status = WebCustomerServiceSessionStatus.CLOSED,
        )
        val session = sessionRepository.save(
            (current ?: WebCustomerServiceSession(
                entryId = entry.id.orEmpty(),
                visitorId = body.visitorId.trim(),
                createdAt = now,
                lastMessageAt = now,
            )).copy(
                visitorName = WebCustomerServiceRules.trimToNull(body.visitorName),
                visitorPhone = WebCustomerServiceRules.trimToNull(body.visitorPhone),
                visitorEmail = WebCustomerServiceRules.trimToNull(body.visitorEmail),
                sourceUrl = WebCustomerServiceRules.trimToNull(body.sourceUrl),
                referrer = WebCustomerServiceRules.trimToNull(body.referrer),
                userAgent = WebCustomerServiceRules.trimToNull(request.getHeader("User-Agent")),
                updatedAt = now,
            ),
        )
        return WebCustomerServiceCreateSessionResponse(
            session = session.toWebCustomerServiceResponse(entry.name),
            visitorToken = tokenService.sign(entry.id.orEmpty(), session.id.orEmpty(), session.visitorId),
        )
    }

    fun publicMessages(sessionId: String, after: String?, size: Int, request: HttpServletRequest): WebCustomerServiceMessagesResponse {
        val context = visitorContext(sessionId, request)
        val messages = messagesAfter(sessionId, after, size)
        return WebCustomerServiceMessagesResponse(
            session = context.session.toWebCustomerServiceResponse(context.entry.name),
            messages = messages.map { it.toWebCustomerServiceResponse() },
        )
    }

    fun visitorSendText(sessionId: String, content: String, request: HttpServletRequest): WebCustomerServiceMessageResponse {
        val context = visitorContext(sessionId, request)
        requireOpenSession(context.session)
        val body = content.trim()
        if (body.isBlank() || body.length > TEXT_MESSAGE_MAX_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "消息内容不能为空且不能超过5000字")
        }
        return saveMessage(
            session = context.session,
            senderType = WebCustomerServiceSenderType.VISITOR,
            senderId = context.claims.visitorId,
            senderName = WebCustomerServiceRules.trimToNull(context.session.visitorName) ?: "访客",
            contentType = WebCustomerServiceContentType.TEXT,
            content = body,
            imageUrl = null,
        ).toWebCustomerServiceResponse()
    }

    fun visitorSendImage(sessionId: String, file: MultipartFile, request: HttpServletRequest): WebCustomerServiceMessageResponse {
        val context = visitorContext(sessionId, request)
        requireOpenSession(context.session)
        val imageUrl = uploadCustomerServiceImage(file, requestId = null)
        return saveMessage(
            session = context.session,
            senderType = WebCustomerServiceSenderType.VISITOR,
            senderId = context.claims.visitorId,
            senderName = WebCustomerServiceRules.trimToNull(context.session.visitorName) ?: "访客",
            contentType = WebCustomerServiceContentType.IMAGE,
            content = "[图片]",
            imageUrl = imageUrl,
        ).toWebCustomerServiceResponse()
    }

    fun listSessions(
        entryId: String?,
        status: WebCustomerServiceSessionStatus?,
        assigned: String?,
        adminId: String,
        page: Int,
        size: Int,
    ): PagedData<WebCustomerServiceSessionResponse> {
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedSize = size.coerceIn(1, 100)
        val criteria = mutableListOf<Criteria>()
        if (!entryId.isNullOrBlank()) criteria += Criteria.where("entryId").`is`(entryId.trim())
        if (status != null) criteria += Criteria.where("status").`is`(status)
        when (assigned?.trim()?.lowercase()) {
            "unassigned" -> criteria += Criteria.where("assignedAdminId").`is`(null)
            "mine" -> criteria += Criteria.where("assignedAdminId").`is`(adminId)
        }

        val query = Query()
        if (criteria.isNotEmpty()) query.addCriteria(Criteria().andOperator(*criteria.toTypedArray()))
        val total = mongoTemplate.count(query, WebCustomerServiceSession::class.java)
        val items = mongoTemplate.find(
            query
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"))
                .skip(normalizedPage.toLong() * normalizedSize)
                .limit(normalizedSize),
            WebCustomerServiceSession::class.java,
        )
        val entryNames = entryNameMap(items.map { it.entryId })
        return PagedData(
            items = items.map { it.toWebCustomerServiceResponse(entryNames[it.entryId]) },
            total = total,
            page = normalizedPage,
            size = normalizedSize,
        )
    }

    fun adminMessages(sessionId: String, before: String?, size: Int): WebCustomerServiceMessagesResponse {
        val session = sessionById(sessionId)
        val entry = entryById(session.entryId)
        val messages = messagesBefore(sessionId, before, size)
        return WebCustomerServiceMessagesResponse(
            session = session.toWebCustomerServiceResponse(entry.name),
            messages = messages.map { it.toWebCustomerServiceResponse() },
        )
    }

    fun claim(sessionId: String, adminId: String, adminUsername: String): WebCustomerServiceSessionResponse {
        val session = sessionById(sessionId)
        val entry = entryById(session.entryId)
        requireSeat(entry, adminId)
        if (session.status == WebCustomerServiceSessionStatus.CLOSED) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "会话已关闭")
        }
        if (session.assignedAdminId != null && session.assignedAdminId != adminId) {
            throw WebCustomerServiceConflictException("会话已被其他客服接待")
        }
        val now = System.currentTimeMillis()
        val updated = mongoTemplate.findAndModify(
            Query(
                Criteria.where("_id").`is`(sessionId)
                    .and("status").ne(WebCustomerServiceSessionStatus.CLOSED)
                    .orOperator(
                        Criteria.where("assignedAdminId").`is`(null),
                        Criteria.where("assignedAdminId").`is`(adminId),
                    ),
            ),
            Update()
                .set("status", WebCustomerServiceSessionStatus.ACTIVE)
                .set("assignedAdminId", adminId)
                .set("assignedAdminUsername", adminUsername)
                .set("updatedAt", now),
            FindAndModifyOptions.options().returnNew(true),
            WebCustomerServiceSession::class.java,
        ) ?: run {
            val latest = sessionById(sessionId)
            if (latest.status == WebCustomerServiceSessionStatus.CLOSED) {
                throw BusinessException(ErrorCode.INVALID_PARAM, "会话已关闭")
            }
            throw WebCustomerServiceConflictException("会话已被其他客服接待")
        }
        return updated.toWebCustomerServiceResponse(entry.name)
    }

    fun adminSendText(sessionId: String, adminId: String, adminUsername: String, content: String): WebCustomerServiceMessageResponse {
        val session = sessionById(sessionId)
        val entry = entryById(session.entryId)
        requireSeat(entry, adminId)
        requireAssignee(session, adminId)
        requireOpenSession(session)
        val body = content.trim()
        if (body.isBlank() || body.length > TEXT_MESSAGE_MAX_LENGTH) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "消息内容不能为空且不能超过5000字")
        }
        return saveMessage(
            session = session,
            senderType = WebCustomerServiceSenderType.ADMIN,
            senderId = adminId,
            senderName = adminUsername,
            contentType = WebCustomerServiceContentType.TEXT,
            content = body,
            imageUrl = null,
        ).toWebCustomerServiceResponse()
    }

    fun adminSendImage(sessionId: String, adminId: String, adminUsername: String, file: MultipartFile): WebCustomerServiceMessageResponse {
        val session = sessionById(sessionId)
        val entry = entryById(session.entryId)
        requireSeat(entry, adminId)
        requireAssignee(session, adminId)
        requireOpenSession(session)
        val imageUrl = uploadCustomerServiceImage(file, requestId = null)
        return saveMessage(
            session = session,
            senderType = WebCustomerServiceSenderType.ADMIN,
            senderId = adminId,
            senderName = adminUsername,
            contentType = WebCustomerServiceContentType.IMAGE,
            content = "[图片]",
            imageUrl = imageUrl,
        ).toWebCustomerServiceResponse()
    }

    fun release(sessionId: String, adminId: String, adminRole: String): WebCustomerServiceSessionResponse {
        val session = sessionById(sessionId)
        val entry = entryById(session.entryId)
        requireSeat(entry, adminId)
        if (session.status == WebCustomerServiceSessionStatus.CLOSED) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "会话已关闭")
        }
        if (session.assignedAdminId != null && session.assignedAdminId != adminId && !isAdminOrAbove(adminRole)) {
            throw ForbiddenException("只有当前接待客服或管理员可以释放会话")
        }
        val updated = sessionRepository.save(
            session.copy(
                status = WebCustomerServiceSessionStatus.WAITING,
                assignedAdminId = null,
                assignedAdminUsername = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return updated.toWebCustomerServiceResponse(entry.name)
    }

    fun close(sessionId: String, adminId: String, adminRole: String): WebCustomerServiceSessionResponse {
        val session = sessionById(sessionId)
        val entry = entryById(session.entryId)
        requireSeat(entry, adminId)
        if (session.assignedAdminId != null && session.assignedAdminId != adminId && !isAdminOrAbove(adminRole)) {
            throw ForbiddenException("只有当前接待客服或管理员可以关闭会话")
        }
        val now = System.currentTimeMillis()
        val updated = sessionRepository.save(
            session.copy(
                status = WebCustomerServiceSessionStatus.CLOSED,
                closedAt = now,
                updatedAt = now,
            ),
        )
        return updated.toWebCustomerServiceResponse(entry.name)
    }

    fun widgetScript(entryId: String): String {
        val escapedEntryId = escapeJs(entryId)
        return WIDGET_SCRIPT_TEMPLATE.replace("__ENTRY_ID__", escapedEntryId)
    }

    private fun normalizeEntryRequest(request: WebCustomerServiceEntryRequest): WebCustomerServiceEntryRequest {
        val domains = WebCustomerServiceRules.normalizeAllowedDomains(request.allowedDomains)
        if (domains.isEmpty() || domains.size != request.allowedDomains.map { it.trim() }.filter { it.isNotBlank() }.size) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "允许域名至少填写1个，且仅支持 example.com 或 *.example.com")
        }
        if (!WebCustomerServiceRules.isValidThemeColor(request.themeColor)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "主题色必须为 #RRGGBB 格式")
        }
        return request.copy(
            name = request.name.trim(),
            allowedDomains = domains,
            seatAdminIds = request.seatAdminIds.mapNotNull(WebCustomerServiceRules::trimToNull).distinct(),
            welcomeMessage = request.welcomeMessage.trim(),
            themeColor = request.themeColor.trim().lowercase(),
        )
    }

    private fun publicEntry(entryId: String, request: HttpServletRequest): WebCustomerServiceEntry {
        val entry = entryById(entryId)
        if (!entry.enabled) throw NotFoundException("客服入口不可用")
        if (!WebCustomerServiceRules.isSourceAllowed(request.getHeader("Origin"), request.getHeader("Referer"), entry.allowedDomains)) {
            throw ForbiddenException("当前域名未被允许接入该客服入口")
        }
        return entry
    }

    private fun visitorContext(sessionId: String, request: HttpServletRequest): VisitorContext {
        val claims = tokenService.verify(request.getHeader(VISITOR_TOKEN_HEADER))
        if (claims.sessionId != sessionId) throw ForbiddenException("访客令牌与会话不匹配")
        val session = sessionById(sessionId)
        if (session.entryId != claims.entryId || session.visitorId != claims.visitorId) {
            throw ForbiddenException("访客令牌与会话不匹配")
        }
        val entry = publicEntry(session.entryId, request)
        return VisitorContext(entry = entry, session = session, claims = claims)
    }

    private fun messagesAfter(sessionId: String, after: String?, size: Int): List<WebCustomerServiceMessage> {
        val pageable = PageRequest.of(0, size.coerceIn(1, 100))
        val cursor = after?.takeIf { it.isNotBlank() }?.let { messageRepository.findById(it).getOrNull() }
        return if (cursor == null) {
            messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable)
        } else {
            messageRepository.findBySessionIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(sessionId, cursor.createdAt, pageable)
        }
    }

    private fun messagesBefore(sessionId: String, before: String?, size: Int): List<WebCustomerServiceMessage> {
        val pageable = PageRequest.of(0, size.coerceIn(1, 100))
        val cursor = before?.takeIf { it.isNotBlank() }?.let { messageRepository.findById(it).getOrNull() }
        val desc = if (cursor == null) {
            messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
        } else {
            messageRepository.findBySessionIdAndCreatedAtLessThanOrderByCreatedAtDesc(sessionId, cursor.createdAt, pageable)
        }
        return desc.asReversed()
    }

    private fun saveMessage(
        session: WebCustomerServiceSession,
        senderType: WebCustomerServiceSenderType,
        senderId: String?,
        senderName: String?,
        contentType: WebCustomerServiceContentType,
        content: String,
        imageUrl: String?,
    ): WebCustomerServiceMessage {
        val now = System.currentTimeMillis()
        val message = messageRepository.save(
            WebCustomerServiceMessage(
                entryId = session.entryId,
                sessionId = session.id.orEmpty(),
                senderType = senderType,
                senderId = senderId,
                senderName = senderName,
                contentType = contentType,
                content = content,
                imageUrl = imageUrl,
                createdAt = now,
            ),
        )
        sessionRepository.save(
            session.copy(
                lastMessagePreview = preview(contentType, content),
                lastMessageAt = now,
                updatedAt = now,
            ),
        )
        return message
    }

    private fun preview(contentType: WebCustomerServiceContentType, content: String): String =
        if (contentType == WebCustomerServiceContentType.IMAGE) "[图片]" else content.take(80)

    private fun uploadCustomerServiceImage(file: MultipartFile, requestId: String?): String {
        if (file.isEmpty || file.size > IMAGE_MAX_BYTES) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "图片不能为空且不能超过10MB")
        }
        if (!WebCustomerServiceRules.isImageContentTypeAllowed(file.contentType)) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "仅支持 jpg/png/webp/gif 图片")
        }
        val upload = uploadPort.uploadFile(file, CUSTOMER_SERVICE_IMAGE_CATEGORY, requestId, userId = null)
        if (upload.status !in 200..299 || !upload.body.success) {
            throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, upload.body.message)
        }
        return extractUploadUrl(upload.body)
            ?: throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "图片上传结果缺少 URL")
    }

    private fun extractUploadUrl(response: ApiResponse<*>): String? {
        val data = response.data ?: return null
        if (data is Map<*, *>) {
            return URL_FIELDS.firstNotNullOfOrNull { key -> data[key]?.toString()?.takeIf { it.isNotBlank() } }
        }
        return URL_FIELDS.firstNotNullOfOrNull { field ->
            val getterName = "get" + field.replaceFirstChar { it.uppercaseChar() }
            runCatching {
                data.javaClass.methods
                    .firstOrNull { it.parameterCount == 0 && it.name.equals(getterName, ignoreCase = true) }
                    ?.invoke(data)
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

    private fun requireOpenSession(session: WebCustomerServiceSession) {
        if (session.status == WebCustomerServiceSessionStatus.CLOSED) {
            throw BusinessException(ErrorCode.INVALID_PARAM, "会话已关闭")
        }
    }

    private fun requireSeat(entry: WebCustomerServiceEntry, adminId: String) {
        if (adminId !in entry.seatAdminIds) throw ForbiddenException("当前管理员不是该客服入口席位")
    }

    private fun requireAssignee(session: WebCustomerServiceSession, adminId: String) {
        if (session.assignedAdminId != adminId) throw ForbiddenException("请先接待该会话")
    }

    private fun sessionById(sessionId: String): WebCustomerServiceSession =
        sessionRepository.findById(sessionId).getOrNull() ?: throw NotFoundException("客服会话不存在")

    private fun entryById(entryId: String): WebCustomerServiceEntry =
        entryRepository.findById(entryId).getOrNull() ?: throw NotFoundException("客服入口不存在")

    private fun entryNameMap(entryIds: List<String>): Map<String, String> =
        entryRepository.findAllById(entryIds.distinct()).associate { it.id.orEmpty() to it.name }

    private fun toEntryResponse(entry: WebCustomerServiceEntry): WebCustomerServiceEntryResponse =
        WebCustomerServiceEntryResponse(
            id = entry.id.orEmpty(),
            name = entry.name,
            enabled = entry.enabled,
            allowedDomains = entry.allowedDomains,
            seatAdminIds = entry.seatAdminIds,
            welcomeMessage = entry.welcomeMessage,
            themeColor = entry.themeColor,
            createdBy = entry.createdBy,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )

    private fun externalBaseUrl(request: HttpServletRequest): String {
        val proto = request.getHeader("X-Forwarded-Proto")?.substringBefore(',')?.trim().takeUnless { it.isNullOrBlank() }
            ?: request.scheme
        val host = request.getHeader("X-Forwarded-Host")?.substringBefore(',')?.trim().takeUnless { it.isNullOrBlank() }
            ?: request.serverName + if (request.serverPort in setOf(80, 443)) "" else ":${request.serverPort}"
        return "$proto://$host"
    }

    private fun isAdminOrAbove(role: String): Boolean =
        role == "ADMIN" || role == "SUPER_ADMIN"

    private fun escapeJs(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private data class VisitorContext(
        val entry: WebCustomerServiceEntry,
        val session: WebCustomerServiceSession,
        val claims: WebCustomerServiceVisitorClaims,
    )

    companion object {
        const val VISITOR_TOKEN_HEADER = "X-WCS-Visitor-Token"
        const val CUSTOMER_SERVICE_IMAGE_CATEGORY = "customer_service_images"
        private const val TEXT_MESSAGE_MAX_LENGTH = 5000
        private const val IMAGE_MAX_BYTES = 10L * 1024 * 1024
        private val URL_FIELDS = listOf("url", "imageUrl", "fileUrl", "publicUrl", "ossUrl")

        private val WIDGET_SCRIPT_TEMPLATE = """
(function () {
  var pendingScript = document.currentScript;
  if (!document.body && pendingScript && pendingScript.src) {
    var pendingSrc = pendingScript.src;
    document.addEventListener('DOMContentLoaded', function () {
      var clone = document.createElement('script');
      clone.async = true;
      clone.src = pendingSrc;
      document.head.appendChild(clone);
    }, { once: true });
    return;
  }
  if (window.__layababaWebCustomerServiceLoaded) return;
  window.__layababaWebCustomerServiceLoaded = true;
  var script = document.currentScript || Array.prototype.slice.call(document.scripts).find(function (item) {
    return item.src && item.src.indexOf('/api/web-customer-service/widget.js') >= 0;
  });
  if (!script) return;
  var scriptUrl = new URL(script.src);
  var apiRoot = scriptUrl.origin;
  var entryId = scriptUrl.searchParams.get('entryId') || '__ENTRY_ID__';
  var keys = {
    visitorId: 'wcs:' + entryId + ':visitorId',
    sessionId: 'wcs:' + entryId + ':sessionId',
    token: 'wcs:' + entryId + ':visitorToken'
  };
  var visitorId = localStorage.getItem(keys.visitorId) || ((window.crypto && crypto.randomUUID) ? crypto.randomUUID() : ('v-' + Date.now() + '-' + Math.random().toString(16).slice(2)));
  localStorage.setItem(keys.visitorId, visitorId);
  var storedToken = localStorage.getItem(keys.token);
  var storedSessionId = localStorage.getItem(keys.sessionId);
  var state = { open: false, entry: null, session: storedToken && storedSessionId ? { id: storedSessionId } : null, token: storedToken, lastMessageId: null, pollTimer: null };
  var host = document.createElement('div');
  host.id = 'layababa-web-customer-service';
  document.body.appendChild(host);
  var root = host.attachShadow ? host.attachShadow({ mode: 'open' }) : host;
  root.innerHTML = '<style>' +
    ':host{all:initial}.wcs-button{position:fixed;right:24px;bottom:24px;z-index:2147483000;border:0;border-radius:999px;background:var(--wcs-color,#2563eb);color:#fff;width:58px;height:58px;box-shadow:0 14px 30px rgba(15,23,42,.22);font:600 14px system-ui;cursor:pointer}.wcs-panel{position:fixed;right:24px;bottom:92px;z-index:2147483000;width:360px;height:540px;background:#fff;border:1px solid #e5e7eb;box-shadow:0 24px 60px rgba(15,23,42,.24);display:none;flex-direction:column;font-family:system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;color:#111827}.wcs-panel.open{display:flex}.wcs-header{height:54px;background:var(--wcs-color,#2563eb);color:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 14px;font-weight:700}.wcs-close{border:0;background:transparent;color:#fff;font-size:22px;cursor:pointer}.wcs-welcome{padding:10px 14px;background:#f8fafc;color:#475569;font-size:13px;line-height:1.5}.wcs-profile{display:grid;grid-template-columns:1fr;gap:6px;padding:10px 14px;border-bottom:1px solid #eef2f7}.wcs-profile input{height:32px;border:1px solid #d1d5db;padding:0 8px;font-size:13px}.wcs-messages{flex:1;overflow:auto;padding:12px;background:#f8fafc}.wcs-msg{max-width:78%;margin:0 0 10px;padding:8px 10px;border-radius:8px;font-size:14px;line-height:1.45;word-break:break-word}.wcs-visitor{margin-left:auto;background:var(--wcs-color,#2563eb);color:#fff}.wcs-admin,.wcs-system{margin-right:auto;background:#fff;border:1px solid #e5e7eb;color:#111827}.wcs-msg img{max-width:180px;max-height:180px;border-radius:6px;display:block}.wcs-composer{display:flex;gap:8px;padding:10px;border-top:1px solid #e5e7eb}.wcs-composer textarea{flex:1;min-height:42px;max-height:90px;resize:none;border:1px solid #d1d5db;padding:8px;font-size:14px}.wcs-composer button,.wcs-upload{border:0;background:var(--wcs-color,#2563eb);color:#fff;padding:0 12px;font-weight:600;cursor:pointer}.wcs-upload{display:flex;align-items:center}.wcs-upload input{display:none}@media(max-width:640px){.wcs-button{right:16px;bottom:16px}.wcs-panel{left:0;right:0;bottom:0;width:100%;height:78vh;border-left:0;border-right:0}.wcs-msg{max-width:84%}}' +
    '</style><button class="wcs-button" type="button">客服</button><section class="wcs-panel"><header class="wcs-header"><span class="wcs-title">在线客服</span><button class="wcs-close" type="button">×</button></header><div class="wcs-welcome"></div><div class="wcs-profile"><input class="wcs-name" placeholder="姓名（可选）"><input class="wcs-phone" placeholder="电话（可选）"><input class="wcs-email" placeholder="邮箱（可选）"></div><main class="wcs-messages"></main><footer class="wcs-composer"><label class="wcs-upload">图<input class="wcs-file" type="file" accept="image/jpeg,image/png,image/webp,image/gif"></label><textarea class="wcs-input" placeholder="输入消息"></textarea><button class="wcs-send" type="button">发送</button></footer></section>';
  var panel = root.querySelector('.wcs-panel');
  var button = root.querySelector('.wcs-button');
  var close = root.querySelector('.wcs-close');
  var messages = root.querySelector('.wcs-messages');
  var input = root.querySelector('.wcs-input');
  var file = root.querySelector('.wcs-file');
  function request(path, options) {
    options = options || {};
    options.headers = options.headers || {};
    if (state.token) options.headers['X-WCS-Visitor-Token'] = state.token;
    return fetch(apiRoot + path, options).then(function (res) {
      return res.json().then(function (body) {
        if (!res.ok || !body.success) throw new Error(body.message || '请求失败');
        return body.data;
      });
    });
  }
  function ensureSession() {
    if (state.session && state.token) {
      if (state.open && !state.pollTimer) startPolling();
      return Promise.resolve(state.session);
    }
    return request('/api/web-customer-service/public/entries/' + encodeURIComponent(entryId) + '/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        visitorId: visitorId,
        visitorName: root.querySelector('.wcs-name').value,
        visitorPhone: root.querySelector('.wcs-phone').value,
        visitorEmail: root.querySelector('.wcs-email').value,
        sourceUrl: location.href,
        referrer: document.referrer
      })
    }).then(function (data) {
      state.session = data.session;
      state.token = data.visitorToken;
      localStorage.setItem(keys.sessionId, state.session.id);
      localStorage.setItem(keys.token, state.token);
      if (state.open && !state.pollTimer) startPolling();
      return state.session;
    });
  }
  function renderMessage(item) {
    if (!item || root.querySelector('[data-id="' + item.id + '"]')) return;
    var el = document.createElement('div');
    el.className = 'wcs-msg ' + (item.senderType === 'VISITOR' ? 'wcs-visitor' : (item.senderType === 'SYSTEM' ? 'wcs-system' : 'wcs-admin'));
    el.setAttribute('data-id', item.id);
    if (item.contentType === 'IMAGE' && item.imageUrl) {
      var img = document.createElement('img');
      img.src = item.imageUrl;
      img.onclick = function () { window.open(item.imageUrl, '_blank'); };
      el.appendChild(img);
    } else {
      el.textContent = item.content || '';
    }
    messages.appendChild(el);
    state.lastMessageId = item.id;
    messages.scrollTop = messages.scrollHeight;
  }
  function poll() {
    if (!state.open || !state.session || !state.token) return;
    var qs = state.lastMessageId ? '?after=' + encodeURIComponent(state.lastMessageId) + '&size=50' : '?size=50';
    request('/api/web-customer-service/public/sessions/' + encodeURIComponent(state.session.id) + '/messages' + qs)
      .then(function (data) { state.session = data.session; (data.messages || []).forEach(renderMessage); })
      .catch(function () {});
  }
  function startPolling() {
    stopPolling();
    poll();
    state.pollTimer = setInterval(poll, 3000);
  }
  function stopPolling() {
    if (state.pollTimer) clearInterval(state.pollTimer);
    state.pollTimer = null;
  }
  function openPanel() {
    state.open = true;
    panel.classList.add('open');
    if (state.session && state.token) startPolling();
  }
  function closePanel() {
    state.open = false;
    panel.classList.remove('open');
    stopPolling();
  }
  button.onclick = openPanel;
  close.onclick = closePanel;
  root.querySelector('.wcs-send').onclick = function () {
    var content = input.value.trim();
    if (!content) return;
    ensureSession().then(function () {
      return request('/api/web-customer-service/public/sessions/' + encodeURIComponent(state.session.id) + '/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: content })
      });
    }).then(function (message) { input.value = ''; renderMessage(message); }).catch(function (err) { alert(err.message); });
  };
  file.onchange = function () {
    if (!file.files || !file.files[0]) return;
    ensureSession().then(function () {
      var form = new FormData();
      form.append('file', file.files[0]);
      return request('/api/web-customer-service/public/sessions/' + encodeURIComponent(state.session.id) + '/images', {
        method: 'POST',
        body: form
      });
    }).then(renderMessage).catch(function (err) { alert(err.message); }).finally(function () { file.value = ''; });
  };
  request('/api/web-customer-service/public/entries/' + encodeURIComponent(entryId) + '/bootstrap')
    .then(function (entry) {
      state.entry = entry;
      root.host && root.host.style.setProperty('--wcs-color', entry.themeColor || '#2563eb');
      root.querySelector('.wcs-title').textContent = entry.name || '在线客服';
      root.querySelector('.wcs-welcome').textContent = entry.welcomeMessage || '您好，请问有什么可以帮您？';
    }).catch(function () { host.remove(); });
})();
""".trimIndent()
    }
}
