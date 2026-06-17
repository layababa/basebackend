# basebackend 抽取边界

## 已抽取内容

- 通用响应与分页结构：`ApiResponse`、`PagedData`
- 通用错误码与业务异常：`ErrorCode`、`BusinessException` 及常用子类
- 通用请求/响应 DTO：反馈、举报、隐私设置、名片
- 低耦合工具函数：金额安全转换、批量查询、正则转义
- 低耦合 Spring 基础设施：Jackson 配置、安全过滤链、密码编码器、健康检查、缓存响应头过滤器、条件启用的 Sentry 异步异常上报
- Sentry 上报工具：`SentryReporter` 支持 Redis 跨实例去重，不存在 Redis bean 时降级本地去重
- 通用第三方配置入口：`PushDaConfig` 统一 `pushda.*` 配置与专用 RestTemplate
- 通用 Mongo 契约：名片、反馈、举报模型和 repository
- 可选复用功能契约：会议、群接龙、钱包币种配置、前端错误上报、实名认证、用户标签等 DTO/model/repository
- 核心领域契约：版本规则、敏感词、Bot、好友、群申请、红包、贴纸、系统配置、用户封禁、提现、节点等 DTO/model/repository
- 深层核心域契约：用户、会话、消息、用户会话、设备会话、媒体对象、钱包流水、朋友圈等 model/repository
- 通用 DTO 契约：认证、后台管理、消息、朋友圈、节点、群 V3 同步等请求/响应 DTO
- 可选业务契约：宣讲大会 Broadcast 的 DTO/model/repository
- 通用服务与接口：名片 controller/service、Excel 导出服务
- 通知端口与公共处理流程：`OfficialNotificationSender`、反馈/举报 service
- 查询端口与公共风控流程：`UserLookupPort`、`ConversationLookupPort`、敏感词命中检测 service
- 接入方能力端口与通用 HTTP 接口：token 解析、TRTC UserSig、媒体密钥快照、反馈/举报 controller、TRTC controller、媒体密钥 controller
- 后台通用接口：管理员权限切面、钱包流水查询与导出 controller
- 后台媒体密钥广播接口：`AdminMediaKeyController` 通过媒体密钥快照与广播端口复用
- 后台 Bot 管理接口：`AdminBotController` 通过 Bot 管理端口复用
- Bot 开放接口：`BotApiController` 通过 Bot API 端口复用，认证拦截器仍由接入方提供
- 后台公开群管理接口：`AdminPublicGroupController` 通过公开群管理端口复用
- 后台通话诊断接口：`AdminCallController` 通过通话诊断端口复用
- 后台会议管理接口：`AdminMeetingController` 通过会议管理端口复用
- 宣讲会会议开放接口：`MeetingController` 通过会议业务端口与客户端兼容端口复用
- 宣讲大会主接口：`BroadcastController`、`AdminBroadcastController` 通过宣讲端口复用
- 宣讲红包/福袋开放接口：`BroadcastRedPacketController`、`BroadcastLuckyBagController` 通过宣讲互动端口复用
- 公开骰子图片资源接口：`PublicDiceAssetController` 通过内置渲染服务复用
- 应用版本开放接口：`AppVersionController` 复用版本模型与仓库，CI 密钥仍由接入方配置
- 登录蜜罐接口：`LoginHoneypotController` 通过蜜罐记录端口复用，风控策略仍由接入方实现
- 客户端推送接口：`PushController` 通过推送端口复用，设备会话与 PushDa 代理仍由接入方实现
- 客户端启动配置接口：`ClientMonitoringConfigController` 通过客户端配置端口复用，监控配置来源、灰度策略和 token 解析仍由接入方实现
- 历史媒体代理接口：`MediaProxyController` 通过媒体代理端口复用，token 校验、OSS key 解析和 endpoint 仍由接入方实现
- 宣讲运营积分池接口：`OperatorBalanceController` 通过运营积分端口复用，运营身份校验和积分账户实现仍由接入方处理
- 邀请与群二维码接口：`InviteController` 通过邀请端口复用，二维码加解密、群权限和入群申请仍由接入方实现
- 后台音视频用量统计接口：`AdminAvUsageController` 通过音视频用量端口复用，TRTC 统计口径和会议段汇总仍由接入方实现
- 后台消息扩散策略接口：`AdminMessageDeliveryPolicyController` 通过消息扩散策略端口复用，策略缓存、命中规则和投递执行仍由接入方实现
- 后台节点管理接口：`AdminNodeController` 通过节点管理端口复用，节点持久化和 CDN 配置发布仍由接入方实现
- 后台监控配置接口：`AdminMonitoringController` 通过监控配置端口复用，配置持久化、缓存失效和后端 Sentry 重载仍由接入方实现
- 后台媒体解密策略接口：`AdminMediaDecryptController` 通过媒体解密策略端口复用，策略命中规则、全局开关存储和缓存失效仍由接入方实现
- 后台群消息 Signal Pull 配置接口：`AdminGroupMessageSignalController` 通过群消息 Signal 配置端口复用，系统配置读写和缓存失效仍由接入方实现
- 后台反馈处理接口：`AdminFeedbackController` 通过反馈处理端口复用，用户通知、奖励积分和缓存失效仍由接入方实现
- 后台系统配置与违禁词接口：`AdminSystemController` 通过系统管理端口复用，配置缓存和仓库读写仍由接入方实现
- 后台远程调试日志接口：`AdminDebugLogController` 通过调试日志端口复用，设备校验、命令下发、OSS 签名和审计落库仍由接入方实现
- 后台红包对账接口：`AdminRedPacketController` 通过红包对账端口复用，Redis/Mongo 对账基线和落库修复仍由接入方实现
- 后台 Dashboard 统计接口：`AdminDashboardController` 通过统计端口复用，Mongo 聚合、Redis 缓存和在线指标来源仍由接入方实现
- 后台客户端管理接口：`AdminClientController` 通过客户端管理端口复用，在线快照调度、版本规则存储和强制下线动作仍由接入方实现
- 后台管理员认证接口：`AdminAuthController` 通过认证端口复用，登录/2FA 限流、审计与令牌签发仍由接入方实现
- 后台登录安全管理接口：`AdminSecurityController` 通过登录安全端口复用，事件聚合、告警、封禁和系统配置缓存仍由接入方实现
- 后台管理员个人中心接口：`AdminSelfController` 通过个人中心端口复用，管理员认证、Redis 2FA 限流和审计仍由接入方实现
- 后台管理员账号管理接口：`AdminManageController` 通过管理员管理端口复用，角色层级、仓库读写、审计和登录失败计数清理由接入方实现
- 后台用户封禁接口：`AdminBanController` 通过封禁端口复用，通知、会话失效、Mongo 查询和审计仍由接入方实现
- 通话会话审计埋点：`CallSessionAudit` 统一 call-audit 结构化日志
- ASR 开放接口：`AsrController` 通过语音识别端口复用，云厂商 SDK 与密钥配置仍由接入方实现
- 贴纸收藏接口：`StickerController` 通过贴纸端口复用，文件存储与 OSS/CDN 细节仍由接入方实现
- 会议 DTO 超集契约：预约会议、会议权限、分享快照、参与者身份与移除限制等 DTO 统一在 SDK
- 后台聊天管理接口：`AdminChatController` 通过聊天管理端口复用
- 后台举报审核接口：`AdminModerationController` 通过审核管理端口复用
- V3 会话 WebSocket handler：通过会话查询、channel 设备解析与响应发送端口复用
- 钱包提现公共流程：提现后台 controller/service，以及锁、审计、支付通知、用户缓存失效端口
- 后台用户钱包接口：`AdminWalletController` 复用钱包调整 service、流水查询与 Excel 导出能力
- 后台钱包调整公共流程：管理员余额调整 service，复用锁、审计和支付通知端口
- 会话 HTTP 接口：`ConversationController` 通过会话端口复用，消息读取、已读位点和隐藏历史逻辑由接入方实现
- 用户资料 HTTP 接口：`UserController` 通过用户资料端口复用，资料持久化、缓存失效和好友通知由接入方实现
- 好友 HTTP 接口：`FriendController` 通过好友端口复用，好友关系写入、系统会话 fallback 和通知由接入方实现
- 钱包开放接口：`WalletController` 通过钱包端口复用，链上地址、提现、支付密码、充值回调签名和通知由接入方实现
- 宣讲会实时信令：会议聊天、权限、分享、心跳 WebSocket handler 通过会议实时端口复用，会议状态、推送、消息落库和客户端兼容策略由接入方实现
- 朋友圈 HTTP 接口：`MomentController` 通过朋友圈端口复用，动态存储、好友可见性、未读计数和通知策略由接入方实现
- 群消息 V3 同步 HTTP 接口：`GroupV3SyncController` 通过群同步端口复用，同步查询、限流、超时、配置和指标由接入方实现
- 通话中客户端心跳：`CallingHandler` 通过通话心跳端口复用，心跳存储和过期策略由接入方实现
- 可配置会议 TRTC 签名服务：`MeetingTrtcService` 仅在接入方提供 `xinxiwang.meeting.trtc.secret-key` 时装配
- 通用运行时小契约：会话类型扩展函数、WebSocket 协议枚举、WebSocket handler 接口、Netty 心跳处理器、WebSocket 路径路由器、PushDa webhook、置顶消息迁移服务
- WebSocket 指标组件：`WebSocketMetrics` 统一连接数、认证、收发消息和 handler 耗时指标
- 通用 WebSocket handler：`GetMyCallStateHandler`、在线状态查询 handler、待接通通话检查 handler、好友操作 handler 通过通话状态、在线状态、待接通通话、好友操作与响应发送端口复用
- 后台审计日志契约：`AdminAuditLog` 超集模型与 repository

## 暂不抽取内容

- 依赖接入方认证上下文且各项目未统一的 controller，例如依赖 `AuthTokenService` 的接口。
- 接入方通知策略实现，例如具体 `OfficialNotificationService`、APNs 推送、系统账号会话创建等。
- 接入方缓存策略实现，例如 `UserCacheService`、`ConversationCacheService` 的 Redis/Mongo 细节。
- 接入方 WebSocket 连接、广播投递与通话状态存储细节；SDK 仅保留 handler/controller 和端口契约。
- WebSocket protobuf 编解码暂留接入方：`WsCodecService` 直接依赖接入方生成的 `WsEnvelope`、`ChatMessage`、`NewMessage` 等 proto 类型；迁移前需要先让 SDK 持有统一 proto schema 或提供解耦的 codec 端口。
- Netty pipeline 与服务启动暂留接入方：`NettyChannelInitializer` 依赖项目本地 `NettyWebSocketHandler` 和限流组件，`NettyServer` 作为 `SmartLifecycle` 会自动绑定端口；迁移前需要先抽象 pipeline 组件并增加显式条件装配，避免 SDK 依赖后意外启动服务。
- 包含项目密钥的实现；SDK 只保留配置注入入口，不保存密钥值。
- 各项目存在分叉的业务规则、错误码或 DTO，迁移前需要先做兼容设计。
- Apple 登录 DTO 当前依赖接入方 `UserDto/AuthDtos`，而接入方仍保留同名认证 DTO；迁移前需要先统一认证 DTO 边界，避免同包同名类冲突。
- 客户端版本规则保留通用比较逻辑，但默认下载地址由接入方传入，SDK 不保存项目域名。
- 用户、会话、消息等深层核心域在不同项目存在字段差异；当前以已接入的 xianyun 契约为基线，其他项目迁移前需要补齐超集字段和 repository 方法。
- DTO 层当前以已接入的 xianyun 契约为基线；其他项目接入前需比较字段差异，优先向 SDK 补兼容字段，不在接入方保留同包同名 DTO。
- 项目私有配置、密钥、资源文件和环境脚本。

## 后续迁移原则

1. SDK 只放稳定公共 API、通用模型、通用 repository、无状态工具和低耦合基础设施。
2. 某个功能即使只在 A 项目已有，只要后续 B/C 项目可能复用，也可以先抽取公共契约到 SDK。
3. 接入方保留认证、权限、通知、运营策略、项目配置和差异化业务流程。
4. 公共代码迁入 SDK 后，接入方通过远程依赖接入，并删除本地重复源码，避免同包同名类冲突。
5. 接入方统一使用 `main-SNAPSHOT` 远程依赖，SDK 变更合入 `main` 后由 JitPack 构建最新快照。
6. 新增 SDK API 时优先保持向后兼容；确需破坏兼容时先同步更新所有接入方。
