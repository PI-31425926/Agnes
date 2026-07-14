## Why

视频状态更新目前靠前后端各轮询一次（20秒间隔），造成不必要的网络请求和延迟（完成通知最多延迟20秒）。改用 WebSocket 推送后，服务端在视频完成时主动通知前端，实现实时反馈，消除轮询开销。

## What Changes

- **新增 WebSocket 端点** `/api/ws/video`，前端建立 WebSocket 连接后接收视频状态推送
- **服务端推送事件**：`video_status`（每次轮询更新时推送）和 `video_completed`（完成时推送）
- **前端改造**：进入视频 Tab 时建立 WebSocket 连接，接收推送后直接更新 `videoTasks`，不再需要 `setInterval` 轮询
- **保留轮询作为降级**：WebSocket 断开时自动降级为 HTTP 轮询（每30秒）
- **保留服务端轮询** `VideoPollingScheduler` 不变（它是上游 API 状态的来源）

## Capabilities

### New Capabilities

- `video-websocket-push`: WebSocket 实时推送视频状态更新，替代前端定时轮询

## Impact

- **新增**: `VideoWebSocketHandler.java`（WebSocket 处理器）、`WebSocketConfig.java`（配置）
- **修改**: `VideoPollingScheduler.java`（推送逻辑改为复用，或新增推送方法）
- **修改**: `MainView.vue`（视频模块：WebSocket 连接管理、消息处理、降级轮询）
- **不变**: `VideoController.java`（REST API 保留）、`VideoTaskManager.java`、`AgnesVideoService.java`
