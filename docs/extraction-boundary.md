# basebackend 抽取边界

## 已抽取内容

- 通用响应与分页结构：`ApiResponse`、`PagedData`
- 通用错误码与业务异常：`ErrorCode`、`BusinessException` 及常用子类
- 通用请求/响应 DTO：反馈、举报、隐私设置、名片
- 低耦合工具函数：金额安全转换、批量查询、正则转义
- 低耦合 Spring 基础设施：Jackson 配置、安全过滤链、密码编码器、健康检查
- 通用 Mongo 契约：名片、反馈、举报模型和 repository
- 可选复用功能契约：会议 DTO、会议模型和会议 repository

## 暂不抽取内容

- 依赖接入方认证上下文的 controller，例如直接读取 `HttpServletRequest.userId` 或依赖 `AuthTokenService` 的接口。
- 依赖接入方通知策略的 service，例如调用 `OfficialNotificationService` 的反馈/举报处理逻辑。
- 各项目存在分叉的业务规则、错误码或 DTO，迁移前需要先做兼容设计。
- 项目私有配置、密钥、资源文件和环境脚本。

## 后续迁移原则

1. SDK 只放稳定公共 API、通用模型、通用 repository、无状态工具和低耦合基础设施。
2. 某个功能即使只在 A 项目已有，只要后续 B/C 项目可能复用，也可以先抽取公共契约到 SDK。
3. 接入方保留认证、权限、通知、运营策略、项目配置和差异化业务流程。
4. 公共代码迁入 SDK 后，接入方通过远程依赖接入，并删除本地重复源码，避免同包同名类冲突。
5. 接入方统一使用 `main-SNAPSHOT` 远程依赖，SDK 变更合入 `main` 后由 JitPack 构建最新快照。
6. 新增 SDK API 时优先保持向后兼容；确需破坏兼容时先同步更新所有接入方。
