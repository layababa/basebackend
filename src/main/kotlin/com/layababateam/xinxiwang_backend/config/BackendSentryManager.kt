package com.layababateam.xinxiwang_backend.config

import com.layababateam.xinxiwang_backend.controller.AdminMonitoringController
import com.layababateam.xinxiwang_backend.service.cache.SystemConfigCacheService
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * 后端 Sentry / GlitchTip 的统一初始化与动态重载入口。
 *
 * 取代旧的 SentryConfig：把 [Sentry.init] 与事件过滤逻辑收敛到一处，
 * 既负责启动期的属性兜底初始化，也支持运行期从 `system_config` 动态覆盖。
 *
 * 三个生效时机：
 *  1. [initBootstrap] — `@PostConstruct`，仅用属性默认值初始化，**不碰 DB**，
 *     保证 DB/Redis 未就绪时也能捕获启动期异常。
 *  2. [onReady] — `ApplicationReadyEvent` 后，从 DB 读取 `monitoring.glitchtip.server.*`
 *     覆盖项重建，使后台已持久化的配置在重启后自动接管。
 *  3. [reloadFromDb] — 后台 [AdminMonitoringController] 保存后即时调用，无需重启/重发版。
 *
 * 后端独立使用 `server.*` 前缀的 key，与客户端（App）的
 * `monitoring.glitchtip.*` 互不影响，分属不同 GlitchTip 项目。
 */
@Component
class BackendSentryManager(
    private val systemConfigCacheService: SystemConfigCacheService,
    @Value("\${sentry.dsn:}") private val defaultDsn: String,
    @Value("\${sentry.environment:production}") private val defaultEnvironment: String,
    @Value("\${sentry.release:}") private val release: String,
    @Value("\${sentry.server-name:}") private val serverName: String,
    @Value("\${sentry.traces-sample-rate:1.0}") private val defaultTracesSampleRate: Double,
    @Value("\${sentry.send-default-pii:false}") private val sendDefaultPii: Boolean,
) {

    private val log = LoggerFactory.getLogger(BackendSentryManager::class.java)

    companion object {
        /**
         * Sentry/GlitchTip 噪音过滤清单（子串匹配，命中即丢弃，不上报）。
         *
         * 分两类：
         *  1. 运营噪音：高频 warn 日志，按 userId/ip/convId 拆成成千上万条同根因 issue，
         *     无可操作告警价值（慢客户端、扫描器、外部依赖瞬断、客户端鉴权失败等）。
         *  2. 正常业务校验：用户侧可预期的校验失败（消息不存在/红包已领完/撤回超时等），
         *     由 GlobalExceptionHandler 以 WARNING 上报，但不应作为线上告警。
         *
         * 仅做集中过滤，不改动各业务点的日志/异常语义，便于回滚与审计。
         */
        private val NOISE_MESSAGE_PATTERNS = listOf(
            // —— 运营噪音 ——
            "drainPending: failed to scan legacy V1 keys",
            "Client auth failed:",
            "Admin auth failed:", // 后台管理端鉴权拒绝（扫描器/越权探测），预期事件非 bug
            "Redis cleanup skipped during shutdown", // 关机競態下 Redis 已先销毁，优雅兜底 warn
            "Rejected background task", // 重启/线程池饱和时 postAuth/channelInactive 被拒，已降级虚拟线程兜底
            "is OPEN and does not permit", // resilience4j 熔断器开启，预期降级行为非 bug
            "Validation failed for argument", // MethodArgumentNotValid 客户端参数校验失败
            "无法删除官方会话中的消息", // 业务校验
            "类别限制", // 文件大小超过类别限制，业务校验
            "listener container has been stopped", // AMQP 关机时停止监听容器拒收 in-flight 消息
            "UserConversation missing for member", // 缺失会话自愈日志（auto-creating），信息性非错误
            "Rabbit publish degraded", // RabbitMQ 不可用时发布降级兜底日志（已优雅处理）
            "No conversation found for call record", // 通话记录无会话边缘日志
            "对方已将你删除", // 业务校验
            "红包已过期", // 业务校验
            "Rejecting message from non-member", // 非群成员发消息被拒，业务校验
            "pending buffer overflow",
            "Dropping typing_status with oversized memberIds",
            "[TRTC-API] Failed to query room",
            "server_busy suppressed",
            "Media proxy: OSS object missing",
            "Media proxy: bad token",
            "[IM_PUSH] apns send rejected",
            "[APNs] REJECTED",
            "[USER-LOGIN] 登录失败",
            "hit auth replay limit",
            // 优雅兜底/信息性 warn（有 fallback 或仅提示，无告警价值）
            "singleflight wait failed",
            "sync_response hit cap",
            "通知權限未授予",
            // —— 正常业务校验（用户可预期，非告警）——
            "消息不存在",
            "会话不存在",
            "接收方不存在",
            "您不在此群",
            "红包已领完",
            "消息发送已超过2分钟，无法撤回",
            "语音识别服务暂不可用",
            "余额不足",
            "无法在官方会话中发送消息",
            // —— GlitchTip 噪音清理（外部依赖瞬断/响应解析，非代码 bug）——
            // [#344/#348/#6829/#6830] 阿里云 OSS SDK 解析响应失败（多为网关返回非预期响应/瞬断），
            // 已有重试与兜底，按 requestId 拆成大量同根因 issue。
            "Failed to parse the response result",
            // [#4307/#4519/#5428] PushDa 聚合推送到 push-server 的 POST 出现 I/O 错误（网络瞬断），
            // 按 userId 拆成大量同根因 issue，运维信号非业务 bug。
            "[PushDa聚合] push error",
            // [#4995] Redis/Mongo 接收消息超时（基础设施瞬断），有重试兜底。
            "Timeout while receiving message",
            // [#6828] 媒体缩略图生成失败（源文件格式/损坏），降级不影响主流程。
            "Thumbnail generation failed for mediaId",
            // [#7772] "你已被对方拉黑" 与已收录的 "对方已将你删除" 同属私聊好友关系业务校验，
            // 用户可预期，非告警。
            "你已被对方拉黑",
            // [#7070-7760 等] Pushy(HTTP/2) 向 APNs 投递时连接周期性回收导致
            // "APNs send failed for token <token>: Stream closed before a reply was received"，
            // 瞬断且有重试兜底，按 token 拆成大量同根因 issue，运维信号非代码 bug。
            "APNs send failed for token",
            // [#7153] "[IM_PUSH] apns send failed (throwable)" 与已收录的 "apns send rejected"
            // 同属推送投递兜底日志。
            "[IM_PUSH] apns send failed",
            // [#3565] "[TRTC-API] DescribeUserInformation error: AuthFailure..." 属 TRTC 账号
            // 鉴权/权限配置问题（运维侧处理），非业务代码 bug。
            "[TRTC-API] DescribeUserInformation error",
            // [#7737] "JSON 反序列化失败" 为客户端上送了不符合 DTO 结构的请求体，属入参校验类
            // 噪音（与 "Validation failed for argument" 同类），非服务端 bug。
            "JSON 反序列化失败",
            // —— 旧媒体管线瞬态告警（当前架构已改为 OSS 直传/直下，不再代理解密媒体字节，
            //    源码中已无对应 log；以下三类来自仍在线的旧版本/在途事件，按 OSS 最终一致性
            //    超时与历史 redirect 元数据缺失拆成大量同根因 issue，均为预期瞬态噪音）——
            // [#7886/#8110/#8133 等] 旧 /api/media 代理在 redirect 元数据（OSS key）尚未写入时
            // 命中老 URL，客户端/ CDN 竞态，可重试，非媒体投递 bug。
            "media proxy redirect key missing",
            // [#7817] 加密上传后明文对象在 OSS 内尚未物化（最终一致性窗口），有超时重试兜底。
            "encrypted upload plain object not ready",
            // [#8207/#8205/#8204 等] 解密后明文对象在超时窗口（8000ms）内仍未在 OSS 物化，
            // 同属媒体管线最终一致性瞬态，非代码 bug。
            "plain object not ready after",
        )
    }

    /** 当前已生效的 DSN，用于幂等：值未变化时跳过重复 [Sentry.init]。 */
    private val activeDsn = AtomicReference<String?>(null)

    @PostConstruct
    fun initBootstrap() {
        apply(
            dsn = defaultDsn,
            environment = defaultEnvironment,
            tracesSampleRate = defaultTracesSampleRate,
            enabled = true,
            source = "bootstrap(properties)"
        )
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        reloadFromDb()
    }

    /**
     * 从 DB（经缓存）读取后端覆盖项并重建 Sentry。
     * 任一项缺失时回退到对应的属性默认值。线程安全、可重复调用。
     */
    @Synchronized
    fun reloadFromDb() {
        val dsn = systemConfigCacheService.getValue(AdminMonitoringController.KEY_GLITCHTIP_SERVER_DSN)
            ?.takeIf { it.isNotBlank() }
            ?: defaultDsn
        val enabled = systemConfigCacheService.getBooleanValue(
            AdminMonitoringController.KEY_GLITCHTIP_SERVER_ENABLED, true
        )
        val environment = systemConfigCacheService.getValue(AdminMonitoringController.KEY_GLITCHTIP_SERVER_ENVIRONMENT)
            ?.takeIf { it.isNotBlank() }
            ?: defaultEnvironment
        val tracesSampleRate = systemConfigCacheService
            .getValue(AdminMonitoringController.KEY_GLITCHTIP_SERVER_TRACES_SAMPLE_RATE)
            ?.toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?: defaultTracesSampleRate

        apply(dsn, environment, tracesSampleRate, enabled, source = "db-override")
    }

    private fun apply(
        dsn: String,
        environment: String,
        tracesSampleRate: Double,
        enabled: Boolean,
        source: String,
    ) {
        if (!enabled || dsn.isBlank()) {
            if (activeDsn.get() != null) {
                Sentry.close()
                activeDsn.set(null)
                log.warn("Sentry disabled (source={}, enabled={}, dsnBlank={})", source, enabled, dsn.isBlank())
            } else {
                log.warn("Sentry not initialized (source={}, enabled={}, dsnBlank={})", source, enabled, dsn.isBlank())
            }
            return
        }

        val signature = "$dsn|$environment|$tracesSampleRate"
        if (activeDsn.get() == signature) {
            log.debug("Sentry config unchanged (source={}), skip re-init", source)
            return
        }

        Sentry.init { options ->
            options.dsn = dsn
            options.environment = environment
            options.release = release
            options.serverName = serverName
            options.tracesSampleRate = tracesSampleRate
            options.isSendDefaultPii = sendDefaultPii
            options.setTag("app", "rentmsg-backend")
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> filterEvent(event) }
        }
        activeDsn.set(signature)
        log.info(
            "Sentry initialized (source={}): env={}, tracesSampleRate={}, dsnHost={}",
            source, environment, tracesSampleRate, dsnHost(dsn)
        )
    }

    /** 仅取 host 用于日志，避免把含 public key 的完整 DSN 写进日志。 */
    private fun dsnHost(dsn: String): String =
        runCatching { java.net.URI(dsn).host ?: "?" }.getOrDefault("?")

    private fun filterEvent(event: SentryEvent): SentryEvent? {
        // ── 网络/连接类异常类型：直接丢弃 ──
        event.exceptions?.forEach { ex ->
            val exType = ex.type ?: return@forEach
            if (exType.contains("ClientAbortException") ||
                exType.contains("BrokenPipeException") ||
                exType.contains("ClosedChannelException") ||
                // 扫描器探测的 404（GlobalExceptionHandler 已静默，但框架旁路仍可能上报）
                exType.contains("NoResourceFoundException")
            ) {
                return null
            }
            // SocketException: Connection reset / Broken pipe —— 客户端主动断连噪音，非服务端 bug
            val exVal = ex.value ?: ""
            if (exType.contains("SocketException") &&
                (exVal.contains("Connection reset") || exVal.contains("Broken pipe"))
            ) {
                return null
            }
        }

        // ── Sentry SB-version banner（Spring Boot 4.x + sentry 8.12 不兼容提示）──
        // SDK 启动时按行打印 "####" / "## ... ##" 边框横幅，被 logback appender 捕获后
        // 按行拆成多条 issue（纯噪音，SDK 仍正常工作）。所有 banner 行均以 "##" 开头，
        // 据此整体丢弃（纯空格边框行无法用文本子串匹配，故用前缀判定）。
        val bannerMsg = event.message?.formatted?.trimStart()
        if (bannerMsg != null && bannerMsg.startsWith("##")) {
            return null
        }

        // ── 噪音文本：合并 message + 各 exception 的 value/type 后做子串匹配 ──
        // 这些是运营噪音（按 userId/ip 拆开的同根因 warn 日志）与正常业务校验，
        // 不具备可操作的告警价值，集中在此丢弃，避免刷爆 GlitchTip。
        val haystack = buildString {
            append(event.message?.formatted ?: "")
            event.exceptions?.forEach { ex ->
                append('\n'); append(ex.value ?: ""); append('\n'); append(ex.type ?: "")
            }
        }
        if (haystack.contains("Connection reset") ||
            haystack.contains("Broken pipe") ||
            NOISE_MESSAGE_PATTERNS.any { haystack.contains(it) }
        ) {
            return null
        }
        return event
    }
}
