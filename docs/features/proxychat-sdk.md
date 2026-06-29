# AI 代聊 SDK 接入说明

本文面向接入 `basebackend` 的业务后端。SDK 只提供统一 HTTP 契约、Bot 鉴权入口、请求参数校验和 DTO；具体代聊配置存储、私聊会话查找、消息拉取、IM 发送、实时推送由业务仓实现 `ProxyChatPort`。

## 1. SDK 提供的接口

### 管理端接口

管理端接口需要管理员鉴权。

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/admin/proxychat/start` | 开启代聊 |
| `POST` | `/api/admin/proxychat/stop` | 停止代聊 |
| `GET` | `/api/admin/proxychat/query` | 查询某个被代聊用户的代聊列表 |

### BotAI 接口

BotAI 接口使用现有 Bot API 鉴权：

```http
Authorization: Bot <api_key>
```

如果 BotAI 需要通过 WebSocket 接收 `proxychat_new_message`，先调用：

```http
POST /api/bot/ws-token
Authorization: Bot <api_key>
```

返回的 `token` 用于 WebSocket `auth` 消息。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/bot/proxychat/list` | 查询分配给当前 Bot 的代聊任务 |
| `GET` | `/api/bot/proxychat/messages` | 拉取某个代聊会话上下文 |
| `POST` | `/api/bot/proxychat/send` | 以被代聊用户身份发送 IM 消息 |

## 2. 业务仓必须实现的 Port

```kotlin
@Component
class ProxyChatAdapter(
    // 注入业务仓自己的 Repository / MessageService / UserSessionManager 等
) : ProxyChatPort {
    override fun startProxyChat(userId: String, targetIds: List<String>, botUserId: String, metadata: Map<String, String>) {
        // 保存或恢复 userId + targetId + botUserId 配置
    }

    override fun stopProxyChat(userId: String, targetIds: List<String>) {
        // 软关闭配置
    }

    override fun queryProxyChats(userId: String, page: Int, size: Int): ProxyChatPage {
        // 分页返回 ProxyChatDto
    }

    override fun listProxyChatsForBot(botUserId: String): List<ProxyChatDto> {
        // 返回当前 Bot 有效任务
    }

    override fun getProxyChatMessages(
        botUserId: String,
        userId: String,
        targetId: String,
        afterSeqId: Long?,
        beforeSeqId: Long?,
        limit: Int,
    ): ProxyChatMessagesResult {
        // 校验 botUserId 对 userId + targetId 有权限；拉取消息历史或增量
    }

    override fun sendProxyChatMessage(
        botUserId: String,
        userId: String,
        targetId: String,
        content: String,
        contentType: Int,
    ): BotMessageSendResult {
        // 校验权限；以 userId 身份向 targetId 所在私聊会话发送消息
    }
}
```

## 3. 实时接收消息

SDK 定义了统一事件 DTO：`ProxyChatIncomingMessageEvent`。

业务仓在现有 IM 发送入口里判断：

1. 当前会话是私聊。
2. 发送者是 `targetId`。
3. 私聊另一方是已开启代聊的 `userId`。
4. 找到对应 `botUserId`。
5. 向 `botUserId` 推送：

```json
{
  "type": "proxychat_new_message",
  "data": {
    "userId": "anchor_001",
    "targetId": "fan_001",
    "botUserId": "bot_001",
    "conversationId": "conv_001",
    "messageId": "msg_001",
    "seqId": 12,
    "contentType": 0,
    "content": "你好",
    "createdAt": 1700000000000,
    "metadata": {
      "role": "assistant"
    }
  }
}
```

## 4. 请求示例

### 开启代聊

```json
POST /api/admin/proxychat/start
{
  "userId": "anchor_001",
  "targetIds": ["fan_001", "fan_002"],
  "botUserId": "bot_001",
  "metadata": {
    "role": "assistant",
    "scene": "after_live"
  }
}
```

限制：

- `targetIds` 单次 1..50。
- ID 长度 1..64。
- `metadata` 最多 32 组；key ≤ 32；value ≤ 512。

### BotAI 拉取上下文

```http
GET /api/bot/proxychat/messages?userId=anchor_001&targetId=fan_001&afterSeqId=12&limit=50
Authorization: Bot <api_key>
```

### BotAI 发送回复

```json
POST /api/bot/proxychat/send
Authorization: Bot <api_key>
{
  "userId": "anchor_001",
  "targetId": "fan_001",
  "content": "我刚看到消息，马上回复你～",
  "contentType": 0
}
```

返回：

```json
{
  "success": true,
  "message": "OK",
  "code": "0",
  "data": {
    "messageId": "msg_002",
    "conversationId": "conv_001",
    "seqId": 13
  }
}
```

## 5. 建议实现

- `userId + targetId` 建唯一索引，重复开启时更新 `botUserId / metadata / enabled`。
- 停止代聊建议软关闭，不删除记录。
- `getProxyChatMessages` 和 `sendProxyChatMessage` 都必须校验 `botUserId` 是否匹配配置。
- BotAI 消费 `proxychat_new_message` 时按 `conversationId + seqId` 做幂等。
