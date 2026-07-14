## Context

当前 WebSocket 实现有三个问题：1) token 验证只是长度检查；2) `lastKnownStatus` 残留 key；3) 异常断开会话不释放。

## Goals / Non-Goals

**Goals:**
- 使用 `StpUtil.getLoginIdByToken(token)` 真实验证 token 有效性
- 任务删除时清理 `lastKnownStatus`/`lastKnownProgress`
- 添加定期清理过期 session 的定时任务

**Non-Goals:**
- 不改动 WebSocket 消息格式
- 不改动前端代码
- 不引入新的外部依赖

## Decisions

### Decision 1: 使用 `StpUtil.getLoginIdByToken()` 验证
**选择**: 在 `VideoWebSocketHandler.afterConnectionEstablished()` 中调用 `StpUtil.getLoginIdByToken(token)` 验证 token。
**理由**: 这是 Sa-Token 1.38 提供的无副作用 token 验证方法，不会修改当前线程的登录状态。
**备选**: 直接操作 Redis key — 已否决，绕过了 Sa-Token 抽象层。

### Decision 2: 定期清理过期 session
**选择**: 新增 `@Scheduled(fixedDelay = 300000)` 方法，遍历 `userSessions`，移除 5 分钟未活动的 session。
**理由**: 简单高效，不需要额外依赖。5 分钟间隔平衡了准确性和开销。
**备选**: 在 `handleTransportError` 中清理 — 已否决，不够可靠。

### Decision 3: 任务删除时清理 lastKnownStatus
**选择**: 在 `VideoTaskManager.removeTask()` 和 `forceRemoveTask()` 中回调 `VideoPollingScheduler` 清理状态。
**理由**: 最小改动，删除方法已知所有被调用的位置。
**备选**: 在 `pollVideoStatuses()` 中检测 key 不存在时清理 — 已否决，轮询间隔太长。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| `getLoginIdByToken()` 依赖 Redis 存储的 token 数据 | Sa-Token 默认存 Redis，项目已配好，无风险 |
| 定期清理任务增加 CPU 开销 | 5 分钟一次，遍历量极小，无影响 |
| 清理 `lastKnownStatus` 时任务可能正在轮询中 | 先清理再轮询或反之都可能短暂不一致，可接受 |

## Migration Plan

零停机：仅修改现有代码逻辑，无数据结构变更，无 API 变更。

## Open Questions

无。
