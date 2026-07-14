## Why

当前所有对话共享一个 Redis 列表，不支持多轮对话切换。用户想开新话题就得清空 Redis，无法保留历史对话上下文。需要引入 Conversation 概念，支持创建/切换/查看多个对话，每个对话独立维护历史消息。

## What Changes

- **后端**：基于 Redis Hash 按 conversationId 存储对话历史，新增对话 CRUD API
- **前端**：左侧对话列表侧边栏，支持新建/切换/删除对话，右侧显示当前对话消息
- **Redis 存储**：`chat:history:{userId}:{conversationId}` 替代原来的 `chat:history:{userId}`

## Capabilities

### New Capabilities

- `conversation-management`: 创建、切换、删除对话，查看历史对话列表

### Modified Capabilities

- `chat-context-management`: 对话历史存储从单一 key 改为按 conversationId 隔离

## Impact

- **新增**: `ConversationController.java`（对话 CRUD）、`Conversation.java` 实体已存在无需新建
- **修改**: `AgnesService.java` — 历史读写增加 conversationId 参数
- **修改**: `AgnesChatRequest` — 增加 conversationId 字段
- **修改**: `MainView.vue` — 新增侧边栏对话列表 + 对话切换 UI
