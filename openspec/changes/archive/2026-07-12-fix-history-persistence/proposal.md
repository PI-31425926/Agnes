## Why

前端使用 `request` 实例后，聊天历史和图片历史加载代码仍访问 `res.data`（拦截器已解包，`res` 就是数组），导致 `undefined`，刷新页面后历史记录全部丢失。同时后端 `saveHistory()` 的 LTRIM 方向错误，`trim(0, 19)` 保留旧消息丢弃新消息，且 Java 端 subList 裁剪与 Redis 写入重复。

## What Changes

- **修复前端历史加载**：`res.data` → `res`（聊天历史、图片历史）
- **修复聊天历史保存**：改为真正的滑动窗口语义 — 只追加本轮新消息（`rightPushAll`），`trim(key, -20, -1)` 保留最新 20 条，移除 Java 端 subList 裁剪和 `delete` 操作
- **修复前端历史加载**：`res.data` → `res`（聊天历史、图片历史）

## Capabilities

### Modified Capabilities

- `chat-context-management`: 修复前端历史加载和后端滑动窗口实现
- `image-history`: 修复前端图片历史加载

## Impact

- **修改**: `MainView.vue` — 聊天/图片历史加载代码
- **修改**: `AgnesService.java` — `saveHistory()` 重写为真正的 LTRIM 滑动窗口
