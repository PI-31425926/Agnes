## Why

WebSocket 视频推送有三个已知问题：1) Sa-Token 认证只是 token 长度检查，未真正验证；2) `lastKnownStatus` 在任务被删除后残留 key；3) 客户端异常断开时 session 永远留在 map 中。这些问题在生产环境中会导致安全风险和内存泄漏。

## What Changes

- **Sa-Token 真实验证**：`VideoWebSocketHandler` 使用 `SaManager.getTokenInfo()` 或 `StpLogic.getTokenInfo()` 真正验证 token 有效性，拒绝伪造 token
- **`lastKnownStatus` 清理**：当 `VideoTaskManager.removeTask()` 或 `forceRemoveTask()` 被调用时，同步清理 `lastKnownStatus` 和 `lastKnownProgress` 中对应的 key
- **定期清理过期 session**：新增定时任务清理 5 分钟未活动的 WebSocket session，防止异常断开导致的内存泄漏

## Capabilities

### Modified Capabilities

- `video-websocket-push`: 增强 WebSocket 认证真实性、清理残留状态、添加 session 定期清理

## Impact

- **修改**: `VideoWebSocketHandler.java` — 替换简单长度检查为 Sa-Token 真实验证，新增定期清理方法
- **修改**: `VideoPollingScheduler.java` — 在检测到任务被删除时清理 `lastKnownStatus`
- **修改**: `VideoTaskManager.java` — 暴露任务删除事件或提供清理回调
