## Context

当前代码中有两处技术描述与实现不符：
1. `AgnesService.saveHistory()` 使用 delete-then-replace 而非 LTRIM，且 `MAX_HISTORY_MESSAGES = 10` 实际只保留 10 条消息（5 轮），与简历声称的"10 轮（20 条）"不一致。
2. `VideoTaskManager.addTask()` 使用 `size()` + `add()` 两步非原子操作做配额检查，存在 TOCTOU 竞态；`getPendingTasks()` 使用 `members()` 一次性拉取全部而非 SSCAN 分批。

## Goals / Non-Goals

**Goals:**
- 将 chat history 改为 LTRIM 滑动窗口，修正为 20 条消息（10 轮）
- 将视频配额检查改为 Lua 脚本原子操作
- 将视频任务迭代改为 SSCAN 分批扫描

**Non-Goals:**
- 不改动 SSE 流式响应逻辑
- 不改动 TTS、文件上传、日志等其他功能
- 不引入新依赖（仅用现有 RedisTemplate 和 Spring Data Redis 内置支持）

## Decisions

### Decision 1: LTRIM 替代 delete-then-replace
**选择**: 在 `saveHistory()` 中先 `rightPushAll` 追加新消息，再用 `LTRIM key 0 19` 截断到 20 条。
**理由**: LTRIM 是 Redis 原生命令，语义上是真正的滑动窗口。相比 delete-then-replace 避免了中间状态（旧数据已被删但新数据尚未写入）导致的历史丢失。
**备选**: 保持 delete-then-replace 但修正常量为 20 — 已否决，因为简历明确写了 LTRIM。

### Decision 2: Lua 脚本原子配额检查
**选择**: 在 `addTask()` 中执行 Lua 脚本：
```lua
local count = redis.call('SCARD', KEYS[1])
if count >= 5 then return -1 end
redis.call('SADD', KEYS[1], ARGV[1])
return 1
```
**理由**: SCARD + SADD 合并为一步，Redis 单线程保证原子性，彻底消除 TOCTOU。
**备选**: 使用 Redisson RLock — 已否决，引入新依赖且 Lua 更轻量。

### Decision 3: SSCAN 替代 SMEMBERS
**选择**: 用 `redisTemplate.execute()` + `Cursor` 接口实现 SSCAN，COUNT=100。
**理由**: Spring Data Redis 的 `ScanOptions` + `Cursor` 原生支持 SSCAN，无需额外依赖。对大集合避免内存峰值。
**备选**: 保持 SMEMBERS — 已否决，简历明确写了 SSCAN。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| LTRIM 改变历史行为：旧代码在 Java 端 subList，新代码在 Redis 端 LTRIM | 行为等价，都是保留最新 20 条；测试覆盖 |
| Lua 脚本中 SCARD+SADD 原子但 addTask 后续还有 set/expire 操作 | 配额检查是关键临界区，后续的 set/expire 不涉及竞态 |
| SSCAN Cursor 需要在 `redisTemplate.execute()` 中管理连接 | 使用 `redisTemplate.execute(new SessionCallback<>() {})` 或 `executePipelined`，Spring Data Redis 自动管理 |
| 修改现有方法签名可能影响调用方 | 所有方法签名不变，仅内部实现修改 |

## Migration Plan

无数据库迁移。代码修改后直接部署，Redis 数据结构不变（仍是 List/Set），兼容无停机。

## Open Questions

无。
