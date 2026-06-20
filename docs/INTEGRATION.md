# basebackend SDK 接入指南

本文面向**接入 basebackend 的后端服务**（如 `xianyun_backend`），说明如何把本 SDK 引入到一个 Spring Boot 4 / Kotlin 工程并正确跑起来。内容均以仓库真实代码为准。

> 仅需快速了解 SDK 边界（哪些能力由 SDK 提供、哪些留在接入方）请看 [`extraction-boundary.md`](./extraction-boundary.md)。

---

## 1. 概述：接入的核心机制

basebackend 是一个 Kotlin / Spring Boot 4 / Jackson 3 的共享后端 SDK，以 `java-library` 形式发布到 JitPack。它包含约 500+ 个源文件：controller、WebSocket handler、service、`@Configuration`、Mongo model/repository、共享 DTO 与异常契约等。

接入之所以**几乎零显式配置**，是因为一个关键约定：

> **SDK 的根包与接入方的根包相同：`com.layababateam.xinxiwang_backend`。**

接入方的 `@SpringBootApplication` 默认对自身所在包做组件扫描，于是 SDK 里所有 `@Component / @Service / @RestController / @Configuration` 都被一并扫描、自动装配——无需任何 `@Enable*` / `@Import` / 自定义 `@ComponentScan`。

⚠️ **如果接入方主类不在 `com.layababateam.xinxiwang_backend`（或其父包）下，SDK 的 bean 不会被注册。** 此时必须显式声明：

```kotlin
@SpringBootApplication
@ComponentScan(basePackages = ["com.layababateam.xinxiwang_backend", "<你自己的包>"])
class YourApplication
```

除组件扫描外，还有两处装配入口（见 [第 3 步](#3-第-3-步包名与自动装配)）：SDK 自带的 `spring.factories`（自动生效），以及 Actuator 管理上下文的 `.imports`（需接入方手动声明）。

---

## 2. 环境要求

| 项 | 版本 | 说明 |
|----|------|------|
| JDK | **21**（toolchain） | SDK `build.gradle` 锁 `JavaLanguageVersion.of(21)` |
| Kotlin | 2.2.21 | kotlin-jvm + kotlin-spring 插件 |
| Spring Boot | **4.0.3** | Boot 4 默认 Jackson 3 |
| Spring Framework | 7.0.x | 随 Boot 4 |
| Gradle | 用工程自带 wrapper | — |

接入方建议与 SDK 保持一致的 Kotlin 编译参数（Boot 4 + Kotlin 注解目标）：

```groovy
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll '-Xjsr305=strict', '-Xannotation-default-target=param-property'
    }
}
```

---

## 3. 接入步骤

### 第 1 步：引入依赖

SDK 通过 **JitPack** 分发，坐标为 `com.github.layababa:basebackend:<ref>`。

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // 推荐：锁定到具体 commit SHA，保证可复现、可追溯
    implementation 'com.github.layababa:basebackend:5082e47'
}
```

**版本锁定约定**：生产接入请用 **commit SHA**（如 `5082e47`），不要用 `main-SNAPSHOT`——SNAPSHOT 会随 main 漂移，导致不可复现的构建。确定要锁哪个 commit：在 SDK 仓库 `git log` 找到目标提交的短哈希，JitPack 会按需构建该 commit 的产物。

> SDK 内部坐标为 `com.layababateam:basebackend:0.4.0`（`build.gradle`），但对外消费一律走 JitPack 的 `com.github.layababa:basebackend`。

### 第 2 步：提供运行时依赖（重要）

SDK 把几乎所有第三方依赖声明为 **`compileOnly`**——它们**不会**进入 SDK 的发布 POM，因此**接入方必须自行在工程里提供这些运行时依赖**。只有这几类会随 SDK 自动带入：

- `api`（编译期可见）：`jakarta.validation-api`、`protobuf-java`、`protobuf-java-util`
- `implementation`（运行期传递）：`kotlin-reflect`、`caffeine`

其余需要接入方自带。以下为最小依赖清单（与 `xianyun_backend/build.gradle` 实际一致，多数由 Spring Boot starter 覆盖）：

```groovy
dependencies {
    // Spring Boot 4 基座
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.retry:spring-retry:2.0.12'

    // Netty（WebSocket pipeline）
    implementation 'io.netty:netty-all'

    // 指标
    implementation 'io.micrometer:micrometer-registry-prometheus'

    // Jackson 3（见第 5 步，必须含 kotlin module）
    implementation 'tools.jackson.module:jackson-module-kotlin'

    // JWT（Admin 鉴权用）
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly  'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly  'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // Sentry
    implementation 'io.sentry:sentry-spring-boot-starter-jakarta:8.12.0'

    // Excel 导出
    implementation 'org.apache.poi:poi-ooxml:5.3.0'

    // 对象存储：AWS S3 + 阿里云 OSS
    implementation platform('software.amazon.awssdk:bom:2.42.5')
    implementation 'software.amazon.awssdk:s3'
    implementation 'com.aliyun.oss:aliyun-sdk-oss:3.18.2'

    implementation 'org.springframework.security:spring-security-crypto'
    implementation 'org.jetbrains.kotlin:kotlin-reflect'
}
```

> 漏掉某个 `compileOnly` 依赖时，通常表现为启动期 `NoClassDefFoundError` / `ClassNotFoundException`，或某个 SDK bean 装配失败。按缺失类名补对应依赖即可。

### 第 3 步：包名与自动装配

1. **组件扫描**：保证接入方主类在 `com.layababateam.xinxiwang_backend`（或父包）下，或用 `@ComponentScan` 显式纳入该包（见[第 1 节](#1-概述接入的核心机制)）。这样 SDK 的 controller/handler/service/`@Configuration` 全部自动注册。

2. **`spring.factories`（无需接入方操作）**：SDK jar 内置
   `META-INF/spring.factories`：
   ```
   org.springframework.boot.EnvironmentPostProcessor=\
     com.layababateam.xinxiwang_backend.config.NodeIdPostProcessor
   ```
   它在环境初始化阶段补全 `xinxiwang.node.id`。随 jar 自动生效，接入方什么都不用做。

3. **Actuator 管理上下文安全配置（需接入方手动声明）**：SDK 提供 `ManagementSecurityConfig`，但它运行在 **Actuator 独立管理上下文**（典型为 `management.server.port=9090`）。该子上下文**不会**扫描主包，因此不会被组件扫描自动拾取。若你启用了独立管理端口，需在接入方工程中新建：

   `src/main/resources/META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports`
   ```
   com.layababateam.xinxiwang_backend.config.ManagementSecurityConfig
   ```

### 第 4 步：配置项

SDK 读取的配置以 `xinxiwang.*` 为主，外加 `pushda.*`、`aliyun.oss.*`、`app.environment`。下表按功能分组，**值一律用占位符**，请用环境变量注入真实值，切勿把密钥写进仓库。

> 注：本表只列 **SDK 自身读取** 的配置键。接入方还可能有自有 app 级配置（如 `xinxiwang.websocket.pending.*`、`xinxiwang.group-message.*`、`xinxiwang.media.exist-probe.*` 等，由接入方代码读取，不属于 SDK），不在此列。带 ✅ 必填 的项缺失会导致启动失败或功能不可用；未标注的多有代码内默认值。

#### 运行环境
| 键 | 示例/默认 | 说明 |
|----|-----------|------|
| `app.environment` | `${APP_ENV:production}` | 运行环境（`local`/`dev`/`staging`/`production`），影响 fail-fast 与安全开关 |

#### 命名空间隔离（多环境共用中间件时必配）
| 键 | 默认 | 说明 |
|----|------|------|
| `xinxiwang.redis.key-prefix` | 空 | Redis key 前缀隔离 |
| `xinxiwang.mongo.collection-prefix` | 空 | Mongo collection 前缀隔离 |
| `xinxiwang.rabbit.name-prefix` | 空 | RabbitMQ 队列/交换机前缀隔离 |

#### 节点身份
| 键 | 默认 | 说明 |
|----|------|------|
| `xinxiwang.node.id` | `${XINXIWANG_NODE_ID:${HOSTNAME:node-<uuid>}}` | 分布式节点标识，由 `NodeIdPostProcessor` 兜底生成 |

#### WebSocket / 消息（SDK 读取）
| 键 | 默认 | 说明 |
|----|------|------|
| `netty.port` | `9000` | WebSocket 监听端口（`NettyServer`） |
| `xinxiwang.message-sync.guard.enabled` | `true` | 消息同步限流总开关（`MessageSyncGuardService`） |
| `xinxiwang.message-sync.guard.duplicate-window-ms` | `250` | 去重窗口 |
| `xinxiwang.message-sync.guard.v3-query-user-per-second` | `30` | V3 按用户查询限速 |
| `xinxiwang.message-sync.guard.v3-query-conversation-per-second` | `8` | V3 按会话查询限速 |
| `xinxiwang.message-sync.guard.v3-sync-user-per-second` | `40` | V3 按用户同步限速 |
| `xinxiwang.message-sync.guard.v3-sync-conversation-per-second` | `10` | V3 按会话同步限速 |
| `xinxiwang.message-sync.guard.v3-batch-sync-per-second` | `5` | V3 批量同步限速 |
| `xinxiwang.message-sync.guard.read-point-batch-per-second` | `10` | 已读点批量限速 |

#### 媒体加密 / 代理（`MediaKeyRegistry`，staging/prod 缺密钥 fail-fast）
| 键 | 示例 | 说明 |
|----|------|------|
| `xinxiwang.media.master-key-current-id` | `k1` | 当前主密钥 id ✅ |
| `xinxiwang.media.master-keys` | `k1:<base64-32B-key>` | 主密钥集合（`id:base64key`，可多组逗号分隔）✅ |
| `xinxiwang.media.proxy.token-secret` | `<base64-secret>` | 媒体代理签名密钥 ✅ |
| `xinxiwang.media.proxy.cache-ttl-minutes` | `60` | 代理缓存 TTL |
| `xinxiwang.media.proxy.cache-max-bytes` | `1073741824` | 代理缓存上限 |
| `xinxiwang.media.proxy.public-base` | `https://<your-proxy-host>/appserver` | 代理对外基址 |

#### 会议 / TRTC（`MeetingTrtcService`，secret-key 为空时签名抛异常）
| 键 | 说明 |
|----|------|
| `xinxiwang.meeting.trtc.secret-key` | TRTC UserSig 签名密钥，**生产必配**；为空时 `genUserSig` 拒绝签发 ✅ |

#### Admin 鉴权 / 审计
| 键 | 默认 | 说明 |
|----|------|------|
| `xinxiwang.admin.jwt-secret` | `<≥256bit secret>` | Admin JWT 密钥 ✅ |
| `xinxiwang.admin.jwt-expiration` | `28800000` | access token 有效期（ms，8h） |
| `xinxiwang.admin.jwt-refresh-expiration` | `604800000` | refresh token 有效期（ms，7d） |
| `xinxiwang.admin.audit.trusted-proxies` | 空 | 审计取真实 IP 的可信代理列表 |

#### Staging 写保护（仅 staging 共用生产资源时启用）
| 键 | 默认 | 说明 |
|----|------|------|
| `xinxiwang.staging.write-protection.enabled` | `false` | 只读运行时保护总开关 |
| `xinxiwang.staging.allow-business-ws` | `false` | 是否放行业务 WS |
| `xinxiwang.staging.allow-scheduled-tasks` | `false` | 是否放行定时任务 |
| `xinxiwang.staging.allow-rabbit-listeners` | `false` | 是否放行 Rabbit 监听 |

#### 阿里云 OSS（`OssConfig` / `MediaEndpointResolver`）
| 键 | 说明 |
|----|------|
| `aliyun.oss.endpoint-internal` | 内网/加速 endpoint |
| `aliyun.oss.endpoint-public` | 公网 endpoint |
| `aliyun.oss.endpoint-public-direct` | 公网直连（绕加速）endpoint |
| `aliyun.oss.access-key-id` / `access-key-secret` | OSS AK ✅（务必用环境变量注入） |

#### PushDa 推送（`PushDaConfig`，`@ConfigurationProperties(prefix="pushda")`）
| 键 | 默认 | 说明 |
|----|------|------|
| `pushda.enabled` | `true` | 开关 |
| `pushda.app-name` | — | 应用名 |
| `pushda.app-secret` | `<secret>` | 应用密钥 ✅ |
| `pushda.base-url` | `https://api.pushda.xin` | 服务地址 |
| `pushda.connect-timeout-ms` / `read-timeout-ms` | `3000` / `5000` | RestTemplate 超时上界 |

### 第 5 步：Spring Boot 4 / Jackson 3 注意事项

SDK 已从 Jackson 2 全量迁移到 **Jackson 3（`tools.jackson.*`）**，并删除了旧的 `AppConfig.objectMapper()`（J2）bean。SDK 内部组件改为注入 Boot 4 自动装配的 Jackson 3 `JsonMapper`。接入方需注意两点：

1. **运行期必须有 Jackson 3 的 Kotlin module**，否则 Kotlin data class 反序列化会 500：
   ```groovy
   implementation 'tools.jackson.module:jackson-module-kotlin'
   ```
   Boot 4 只在 classpath 存在该 module 时才注册 Kotlin 支持。

2. **若接入方自有代码仍依赖 Jackson 2 的 `com.fasterxml.jackson.databind.ObjectMapper`**（例如自有 service 构造注入它，或 `jjwt-jackson` 依赖 J2），需在接入方工程补回一个等价的 J2 `ObjectMapper` bean。J2 `ObjectMapper` 与 Boot 4 的 J3 `JsonMapper` 类型不同、各司其职，不冲突：

   ```kotlin
   @Configuration
   class LocalJacksonConfig {
       @Bean
       fun objectMapper(): ObjectMapper =
           ObjectMapper()
               .registerKotlinModule()
               .registerModule(JavaTimeModule())
               .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
               .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
               .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
   }
   ```

### 第 6 步：使用 SDK 能力

SDK 的 service/config 被自动装配为 Spring bean，接入方直接**构造器注入**即可。`xianyun_backend` 的真实用法示例：

| SDK 类型 | 接入方用途 |
|----------|-----------|
| `MeetingTrtcService` | `genUserSig()` 生成腾讯 TRTC UserSig |
| `MediaKeyRegistry` | 取媒体加密主密钥 |
| `MediaCryptoService` | 消息/媒体加解密 |
| `MediaProxyTokenService` | 媒体代理访问令牌签发/校验 |
| `MediaEndpointResolver` | OSS endpoint 解析 |
| `DistributedLockService` | 跨节点分布式锁（钱包事务、ban 过期、已读点 flush 等） |
| `AuthTokenService` | WebSocket 鉴权 |
| `List<MessageHandler>` | 注入全部 WS 消息 handler，按类型分发 |

**实现 SDK 的 Port 接口**：部分能力 SDK 只定义契约（`*Port`），由接入方实现并交回 SDK（控制反转）。例如 `xianyun_backend` 的 `MediaProxyAdapter` 实现了 `MediaProxyPort`。需要扩展时，在接入方写一个实现 `*Port` 的 `@Component` 即可被 SDK 装配使用。

```kotlin
@Component
class MediaProxyAdapter(
    private val tokenService: MediaProxyTokenService,
    private val endpointResolver: MediaEndpointResolver,
) : MediaProxyPort {
    // ... 委托给 SDK service 实现契约
}
```

---

## 4. 可观测性（Micrometer 指标）

SDK 的 `WebSocketMetrics`（`@Component`，注入 `MeterRegistry`）暴露以下指标。接入方接入 `micrometer-registry-prometheus` + Actuator 即可在 `/actuator/prometheus` 抓取（Prometheus 命名把 `.` 转为 `_`）：

| 指标 | 类型 | 标签 | 含义 |
|------|------|------|------|
| `ws.connections.active` | Gauge | — | 活跃连接数（channel 数，含多端/重连） |
| `ws.online.users` | Gauge | — | **去重在线人数**（按 userId），需接入方主动绑定，见下 |
| `ws.messages.sent` | Counter | — | 下发消息总数 |
| `ws.messages.received.typed` | Counter | `type` | 按类型接收消息数 |
| `ws.auth` | Counter | `result=success/failure` | WS 鉴权成功/失败 |
| `ws.handler.duration` | Timer | `type` | 按类型的 handler 处理耗时 |
| `ws.connections.version` | Gauge | `platform`,`version` | 按端/版本的连接数 |

**绑定去重在线人数（推荐）**：`ws.online.users` 默认不发射，需要接入方在持有「去重在线用户表」（key 为 userId）的组件里，于 `@PostConstruct` 调用一次（幂等）：

```kotlin
@PostConstruct
fun bindMetrics() {
    webSocketMetrics.bindOnlineUsersGauge { userChannels.size }
}
```

> 这是有意的依赖反转：用 supplier 回调避免 SDK 的指标类与接入方的会话管理器形成构造循环。

Actuator/Prometheus 暴露的典型配置（管理端口与主端口分离时）：

```properties
management.server.port=9090
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.prometheus.enabled=true
```

---

## 5. 排错（Troubleshooting）

| 现象 | 可能原因 | 处理 |
|------|---------|------|
| SDK 的 controller/handler/service 全部不生效 | 接入方主类不在 `com.layababateam.xinxiwang_backend` 下，组件未被扫描 | 用 `@ComponentScan` 显式纳入该包（[第 3 步](#3-第-3-步包名与自动装配)） |
| 启动期 `NoClassDefFoundError` / `ClassNotFoundException` | 漏装某个 `compileOnly` 运行时依赖 | 按缺失类补对应依赖（[第 2 步](#2-第-2-步提供运行时依赖重要)） |
| Kotlin data class 反序列化 500 / 字段全空 | 运行期缺 Jackson 3 kotlin module | 加 `tools.jackson.module:jackson-module-kotlin`（[第 5 步](#第-5-步spring-boot-4--jackson-3-注意事项)） |
| 自有代码注入 `ObjectMapper` 启动失败（NoSuchBean） | SDK 迁移 J3 后已删除 J2 `objectMapper` bean | 在接入方补回 `LocalJacksonConfig`（[第 5 步](#第-5-步spring-boot-4--jackson-3-注意事项)） |
| 启动报媒体密钥相关异常（staging/prod） | `xinxiwang.media.master-keys` 等未配，`MediaKeyRegistry` fail-fast | 配齐媒体密钥；本地可用 `app.environment=local` 兜底 |
| `genUserSig` 抛异常 / TRTC 进不去房间 | `xinxiwang.meeting.trtc.secret-key` 为空 | 配置 TRTC 签名密钥（SDK 拒绝用空密钥签出无效 UserSig） |
| 老记录反序列化 500（如 CheckinRecord） | 历史文档缺新字段 | SDK 已为历史字段加默认值；确认锁定的 commit 含该修复 |
| 网络抖动时 WS 线程被 OSS 拖死 | OSS 客户端超时/重试过宽 | SDK 已收紧（连接超时 3s、最多重试 1 次）；确认用的是含该修复的 commit |
| 死连接 / 半开连接不回收 | — | SDK 用 `IdleStateHandler` readerIdle + `dropPongFrames(false)` 已修；确认 commit 包含 |
| 管理端口（9090）安全配置不生效 | 管理上下文不扫主包 | 新建 `ManagementContextConfiguration.imports` 注册 `ManagementSecurityConfig`（[第 3 步](#3-第-3-步包名与自动装配)） |

---

## 6. 附录

- **SDK 封装边界**（哪些由 SDK 提供 / 哪些留接入方）：[`extraction-boundary.md`](./extraction-boundary.md)
- **近期重要变更**（接入方升级 commit 时关注）：
  - OSS 客户端超时+重试收紧（防 WS 假死）
  - 新增 `ws.online.users` 去重在线人数 gauge
  - WebSocket `IdleStateHandler` 改 readerIdle，修死/半开连接不回收
  - `CheckinRecord` 历史字段加默认值，修旧记录反序列化 500
  - Jackson 2→3 全量迁移 + TRTC secret-key 守卫 + 媒体密钥 fail-fast
