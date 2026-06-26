# 网页客服 v1

## 范围

网页客服 v1 提供轻量嵌入式客服能力：

- 后台创建客服入口，配置允许域名、客服席位、欢迎语和主题色。
- 客户网站嵌入 `widget.js?entryId=...` 后显示右下角浮动客服组件。
- 访客匿名创建会话，可选填写姓名、电话、邮箱，支持文字和图片消息。
- 后台客服在工作台查看队列、认领、回复、释放回队列、关闭会话。

本版本不包含机器人、FAQ、SLA、报表、WebSocket、Dart SDK 改动或通用文件附件。

## 后端

代码位置：`basebackend`

核心文件：

- `model/WebCustomerService.kt`
- `dto/WebCustomerServiceDtos.kt`
- `repository/WebCustomerServiceEntryRepository.kt`
- `repository/WebCustomerServiceSessionRepository.kt`
- `repository/WebCustomerServiceMessageRepository.kt`
- `service/WebCustomerServiceRules.kt`
- `service/WebCustomerServiceTokenService.kt`
- `service/WebCustomerServiceService.kt`
- `controller/WebCustomerServiceController.kt`
- `controller/AdminWebCustomerServiceController.kt`
- `config/WebCustomerServiceCorsFilter.kt`

### 配置

生产和预发布环境必须配置：

```properties
xinxiwang.web-customer-service.visitor-token-secret=...
```

当 `spring.profiles.active` 包含 `staging`、`prod` 或 `production` 且该配置为空时，服务启动会失败。开发环境未配置时使用本地默认 secret，避免影响本地启动。

### 访客 token

访客 token 由 HMAC-SHA256 签名，包含：

- `entryId`
- `sessionId`
- `visitorId`
- `exp`

以下公开接口需要 `X-WCS-Visitor-Token`：

- `GET /api/web-customer-service/public/sessions/{sessionId}/messages`
- `POST /api/web-customer-service/public/sessions/{sessionId}/messages`
- `POST /api/web-customer-service/public/sessions/{sessionId}/images`

### 域名白名单

公开入口会校验请求来源：

- 优先使用 `Origin`
- 无 `Origin` 时回退到 `Referer`
- 域名会转小写并去掉端口
- 支持精确域名，例如 `example.com`
- 支持通配子域名，例如 `*.example.com`

入口停用或来源域名不在白名单时，公开接口会拒绝访问。

### 公开 API

- `GET /api/web-customer-service/widget.js?entryId={id}`
- `GET /api/web-customer-service/public/entries/{entryId}/bootstrap`
- `GET /api/web-customer-service/public/default-entry/bootstrap`
- `POST /api/web-customer-service/public/entries/{entryId}/sessions`
- `GET /api/web-customer-service/public/sessions/{sessionId}/messages?after={messageId}&size=50`
- `POST /api/web-customer-service/public/sessions/{sessionId}/messages`
- `POST /api/web-customer-service/public/sessions/{sessionId}/images`

`default-entry/bootstrap` is for native or host-integrated clients that do not want to compile an entry ID into the app. It scans enabled entries in service order and returns the first entry allowed by the same Origin/Referer domain check. If no enabled entry is accessible, it returns 404.

### 后台 API

- `GET /api/admin/web-customer-service/entries`
- `POST /api/admin/web-customer-service/entries`
- `PUT /api/admin/web-customer-service/entries/{id}`
- `GET /api/admin/web-customer-service/entries/{id}/script`
- `GET /api/admin/web-customer-service/sessions?entryId=&status=&assigned=unassigned|mine|all&page=&size=`
- `GET /api/admin/web-customer-service/sessions/{id}/messages?before=&size=50`
- `POST /api/admin/web-customer-service/sessions/{id}/claim`
- `POST /api/admin/web-customer-service/sessions/{id}/messages`
- `POST /api/admin/web-customer-service/sessions/{id}/images`
- `POST /api/admin/web-customer-service/sessions/{id}/release`
- `POST /api/admin/web-customer-service/sessions/{id}/close`

### 权限

- 入口 CRUD 和脚本复制：`@RequireAdmin("ADMIN")`
- 工作台会话和消息 API：`@RequireAdmin("MODERATOR")`
- 认领、回复、释放、关闭还要求当前管理员在入口 `seatAdminIds` 内。
- 释放和关闭允许当前接待人操作；入口席位内的 `ADMIN` / `SUPER_ADMIN` 可强制操作。

### 图片

新增媒体分类：`customer_service_images`

限制：

- 最大 10 MB
- 允许 `jpg`、`png`、`webp`、`gif`
- 复用 `UploadPort.uploadFile(file, "customer_service_images", requestId, userId = null)`

## Widget

后台返回嵌入脚本：

```html
<script async src="https://api.example.com/api/web-customer-service/widget.js?entryId=ENTRY_ID"></script>
```

widget 特性：

- 纯原生 JS，无外部依赖。
- 使用 Shadow DOM 隔离客户站点 CSS。
- 本地存储：
  - `wcs:{entryId}:visitorId`
  - `wcs:{entryId}:sessionId`
  - `wcs:{entryId}:visitorToken`
- 展示右下角浮动按钮、聊天面板、欢迎语、消息列表、文字输入、图片上传和可选访客资料。
- 面板打开时每 3 秒轮询当前会话消息；关闭或最小化后停止轮询。
- 小屏幕下变为底部全宽面板。

## 后台页面

代码位置：`template_admin`

新增菜单组：`网页客服`

页面：

- `app/pages/customer-service/entries.vue`
- `app/pages/customer-service/workbench.vue`

### 客服入口

支持：

- 查看入口列表
- 创建和编辑入口
- 配置启用状态、允许域名、客服席位、欢迎语和主题色
- 查看并复制嵌入脚本

席位候选来自既有 `/api/admin/admins`，只显示激活的 `MODERATOR`、`ADMIN`、`SUPER_ADMIN`。

### 接待工作台

三栏布局：

- 左侧：`待接待`、`我的接待`、`已关闭`
- 中间：消息记录、文字回复、图片上传、接待/释放/关闭操作
- 右侧：访客资料、来源 URL、Referrer、User Agent、入口和席位信息

轮询：

- 会话列表每 5 秒刷新。
- 当前会话消息每 3 秒刷新。

## 状态机

- 新会话状态为 `WAITING`
- 认领后变为 `ACTIVE` 并记录客服 ID 和用户名
- 并发认领同一会话时，后到请求返回 409
- 释放后清空接待人并回到 `WAITING`
- 关闭后变为 `CLOSED`
- 关闭后的旧 token 仍可轮询历史消息，但不能继续发送消息
- 同一访客再次创建会话时，只复用未关闭会话；已关闭会话不会复用

## 验证

后端：

```powershell
$env:JAVA_HOME='D:\tmp\temurin-jdk21\jdk-21.0.11+10'
.\gradlew.bat test
```

后台：

```powershell
npm run build
node --test test/*.mjs
```

本地预览：

```powershell
npm run dev -- --host 127.0.0.1 --port 3023
```

访问 `http://127.0.0.1:3023/admin/`。
