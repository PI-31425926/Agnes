## 1. Sa-Token WebSocket 真实验证

- [x] 1.1 在 `VideoWebSocketHandler.afterConnectionEstablished()` 中用 `StpUtil.getLoginIdByToken(token)` 替代长度检查
- [x] 1.2 验证失败（返回 null）时拒绝连接，返回 401

## 2. lastKnownStatus 清理

- [x] 2.1 在 `pollVideoStatuses()` 中收集当前 videoId 集合，用 `removeIf` 清理残留 key
- [x] 2.2 在 `pollVideoStatuses()` 中同步清理 `lastKnownProgress`

## 3. 定期清理过期 session

- [x] 3.1 在 `VideoWebSocketHandler` 中添加 `@Scheduled(fixedDelay = 300000)` 清理 5 分钟未活动 session
- [x] 3.2 使用 `sessionLastAccess` Map 手动记录最后访问时间（`WebSocketSession` 无 `getLastAccessTime()`）
- [x] 3.3 清理时从 `userSessions` map 中移除已关闭或长时间未活动的用户
