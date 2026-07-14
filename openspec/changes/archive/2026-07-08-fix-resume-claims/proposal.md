## Why

简历中 Claim 2（流式对话上下文管理）和 Claim 3（异步任务调度与配额管控）的技术描述与实际代码不符，存在 LTRIM 不存在、消息轮数夸大、SSCAN 不存在、Lua 脚本不存在、配额检查非原子等问题。将代码改为匹配简历描述，确保面试时可准确阐述实现细节。

## What Changes

- **流式对话上下文管理**：将 Redis List 的滑动窗口维护从"delete-then-replace"改为使用 `LTRIM` 命令，保持真正的滑动窗口语义；修正消息数量为 10 轮（20 条消息）
- **异步任务调度**：在 `VideoTaskManager.addTask()` 中使用 Redis Lua 脚本实现原子化的配额扣减（读+写一步完成），消除 TOCTOU 竞态；使用 `SSCAN` 替代 `SMEMBERS` 分批迭代待处理任务

## Capabilities

### Modified Capabilities

- `chat-context-management`: 滑动窗口从 delete-then-replace 改为 LTRIM，消息上限从 10 条改为 20 条（10 轮）
- `video-quota-control`: 配额检查从非原子 size()+add() 改为 Redis Lua 脚本原子操作；任务迭代从 SMEMBERS 改为 SSCAN

## Impact

- `AgnesService.java` — saveHistory/getHistory 方法重写
- `VideoTaskManager.java` — addTask() 改用 Lua 脚本，getPendingTasks() 改用 SSCAN
- `VideoPollingScheduler.java` — 无变化（已 20 秒轮询）
