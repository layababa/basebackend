# basebackend 抽取边界

## 已抽取内容

- 通用响应与分页结构：`ApiResponse`、`PagedData`
- 通用错误码与业务异常：`ErrorCode`、`BusinessException` 及常用子类
- 通用请求/响应 DTO：反馈、举报、隐私设置、名片
- 低耦合工具函数：金额安全转换、批量查询、正则转义
- 低耦合 Spring 基础设施：Jackson 配置、安全过滤链、密码编码器、健康检查、条件启用的 Sentry 异步异常上报
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
- 后台聊天管理接口：`AdminChatController` 通过聊天管理端口复用
- 后台举报审核接口：`AdminModerationController` 通过审核管理端口复用
- V3 会话 WebSocket handler：通过会话查询、channel 设备解析与响应发送端口复用
- 钱包提现公共流程：提现后台 controller/service，以及锁、审计、支付通知、用户缓存失效端口
- 后台钱包调整公共流程：管理员余额调整 service，复用锁、审计和支付通知端口
- 可配置会议 TRTC 签名服务：`MeetingTrtcService` 仅在接入方提供 `xinxiwang.meeting.trtc.secret-key` 时装配
- 通用运行时小契约：会话类型扩展函数、WebSocket 协议枚举、WebSocket handler 接口、Netty 心跳处理器、WebSocket 路径路由器、PushDa webhook、置顶消息迁移服务
- WebSocket 指标组件：`WebSocketMetrics` 统一连接数、认证、收发消息和 handler 耗时指标
- 通用 WebSocket handler：`GetMyCallStateHandler`、好友操作 handler 通过通话状态、好友操作与响应发送端口复用
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
