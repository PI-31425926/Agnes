## Context

前端改用 `request` 实例（Axios 拦截器自动解包 `ApiResponse.data`）后，聊天和图片历史加载代码仍访问 `res.data`，导致 `undefined`，历史记录永不加载。同时后端 `saveHistory()` 的 LTRIM 方向错误——`rightPushAll` 追加后 `trim(0, 19)` 保留旧数据丢弃新数据，且 Java 端 subList 裁剪与 Redis 写入重复。

## Goals / Non-Goals

**Goals:**
- 修复前端历史加载：`res.data` → `res`
- 修复后端历史保存：真正的滑动窗口语义 — 只追加本轮新消息，`trim(key, -20, -1)` 保留最新 20 条
- 移除 Java 端冗余的 subList 裁剪
- 零依赖变更

**Non-Goals:**
- 不增加持久化到 MySQL
- 不改变 Redis TTL 策略（30 分钟）

## Decisions

### Decision 1: 前端直接访问 res
**选择**: `res` 已是解包后的数组/List，直接 `res.length > 0`。
**理由**: Axios 拦截器已返回 `apiRes.data`，无需再 `.data`。

### Decision 2: 真正的 LTRIM 滑动窗口
**选择**: `rightPushAll` 只追加本轮 2 条新消息 + `trim(key, -20, -1)` 保留最新 20 条。
**理由**: 
- 真正的滑动窗口语义——旧数据自然被 trim 掉，不需要先 delete
- 无中间空窗期（delete-then-push 在两次操作间其他请求可能读到空数据）
- 移除 Java 端 subList 裁剪，逻辑收敛到 Redis 一侧

```
Before (broken):
  rightPushAll(history)      → 追加全部历史（含旧数据副本）
  trim(0, 19)                → 保留最左边 20 条 = 旧数据优先 ✓ 错

After (correct):
  rightPushAll(userMsg, assistantMsg)  → 只追加本轮 2 条新消息
  trim(-20, -1)                        → 保留最右边 20 条 = 最新数据 ✓ 对
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| `trim(key, -20, -1)` 负索引行为 | Redis 官方支持负索引：-1 = 最后元素，-20 = 倒数第 20 个 |
| 首次对话 Redis 无 key 时 rightPushAll 行为 | rightPushAll 在 key 不存在时自动创建，trim 安全 |
| 前端改动不影响现有功能 | 仅修正属性访问路径，不改变数据流 |

## Migration Plan

零停机部署，仅修复逻辑错误。

## Open Questions

无。
