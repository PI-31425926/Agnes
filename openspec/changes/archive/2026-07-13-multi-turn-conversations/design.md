## Context

当前所有对话共享 `chat:history:{userId}` 一个 Redis key，无法区分不同话题。`Conversation` 实体已存在但未使用。需要引入 Conversation 概念，将历史消息按 conversationId 隔离。

## Goals / Non-Goals

**Goals:**
- 支持创建/切换/删除/列表多个对话
- 每个对话独立维护 Redis 历史消息
- 新增首个消息自动命名对话
- 前端新增左侧对话列表侧边栏

**Non-Goals:**
- 不修改现有 `Conversation` 实体结构（已有 id, userId, title, createdAt）
- 不迁移旧数据（旧数据仍在 `chat:history:{userId}` 下，新数据用 `chat:history:{userId}:{conversationId}`）
- 不改动视频/图片功能

## Decisions

### Decision 1: Redis key 按 conversationId 隔离
**选择**: `chat:history:{userId}:{conversationId}` 替代 `chat:history:{userId}`。
**理由**: 最小改动——只需在 key 中加入 conversationId，现有 LTRIM 滑动窗口逻辑不变。
**备选**: 改用 MySQL 存储对话历史 — 已否决，Redis 更适合短生命周期对话数据。

### Decision 2: 对话列表存 MySQL，历史存 Redis
**选择**: Conversation 元数据（id, title, createdAt）存 MySQL，消息历史存 Redis。
**理由**: Conversation 是低频查询（列表展示），MySQL 合适；消息历史是高频追加/读取，Redis 合适。
**备选**: 全部存 MySQL — 已否决，MySQL 不适合高频追加操作。

### Decision 3: 前端侧边栏 + 主聊天区布局
**选择**: 左侧固定宽度侧边栏（对话列表），右侧聊天区。
**理由**: 符合主流聊天应用（微信、ChatGPT）的交互模式，用户熟悉。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| 旧数据不兼容 | 默认创建新对话，旧数据可通过手动迁移脚本处理 |
| 对话列表加载慢（大量对话） | 分页加载或虚拟滚动，当前用户量少无需优化 |
| Redis key 爆炸（每个对话一个 key） | 每个对话最多 20 条消息，TTL 30 分钟自动清理 |

## Migration Plan

1. 后端新增 `ConversationController`（CRUD API）
2. 修改 `AgnesService` 历史读写增加 conversationId 参数
3. 前端新增侧边栏 + 对话切换逻辑
4. 旧对话数据保留在 `chat:history:{userId}` 下，新对话使用 `chat:history:{userId}:{conversationId}`

## Open Questions

无。
