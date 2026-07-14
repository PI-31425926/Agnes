## Context

当前视频状态更新依赖前后端各轮询一次（20秒间隔）。前端进入视频 Tab 后每20秒 GET `/api/video/tasks`，服务端 `VideoPollingScheduler` 每20秒轮询上游 API。这导致：
- 完成通知最多延迟20秒
- 空闲时每20秒产生一次 HTTP 请求
- 多用户时每个用户都有独立轮询，浪费资源

## Goals / Non-Goals

**Goals:**
- 服务端检测到视频状态变化时主动推送到前端 WebSocket
- 前端接收推送后实时更新 UI，消除轮询延迟
- WebSocket 断开时自动降级为 HTTP 轮询
- 保留服务端轮询机制（它是上游状态的唯一来源）

**Non-Goals:**
- 不使用 WebSocket 创建视频任务（REST API 保持不变）
- 不替换服务端 `VideoPollingScheduler`
- 不处理聊天等其他功能的 WebSocket

## Decisions

### Decision 1: Spring WebSocket + Stomp over raw WebSocket
**选择**: 使用 Spring WebSocket（`WebSocketHandler` + `TextWebSocketHandler`），不用 STOMP 协议栈（太重）。
**理由**: 项目只需简单的文本消息推送，STOMP 的订阅/发布模型过于复杂。直接用 `TextWebSocketHandler` 发送 JSON 字符串足够。
**备选**: raw WebSocket via SockJS — 已否决，增加复杂度且项目不需要。

### Decision 2: 服务端推送由 VideoPollingScheduler 触发
**选择**: 在 `VideoPollingScheduler` 遍历任务时，对比新旧状态，如有变化则通过 WebSocket 推送。
**理由**: 复用已有的轮询逻辑，不需要新建独立的推送触发器。`VideoTaskManager` 维护一个 `Map<String, Set<WebSocketSession>>` 按 userId 分组 session。
**备选**: 新建独立的 `VideoStatusObserver` 观察者 — 已否决，增加组件数量。

### Decision 3: 前端连接管理封装为 composable
**选择**: 在 `MainView.vue` 视频模块中封装 `useVideoWebSocket()` 逻辑（或用内联函数）。
**理由**: 项目前端是 `<script setup>` 单文件组件，不适合拆单独的 composable 文件。直接在 video 模块内管理连接。
**备选**: 抽离为独立 `.vue` 组件 — 已否决，增加文件数量且视频模块代码不多。

### Decision 4: WebSocket 认证通过 URL 参数传递 token
**选择**: `new WebSocket(url + '?token=' + token)`，服务端握手时从 query 解析 token 验证。
**理由**: 简单可靠，Sa-Token 已有 `SaTokenConfig` 拦截器经验。不需要额外的 JWT 解析。
**备选**: HTTP upgrade header 传 token — 已否决，前端 `new WebSocket()` 不支持自定义 header。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| WebSocket 连接不稳定（移动端网络切换） | 前端自动重连 + 降级轮询兜底 |
| 服务端重启后 WebSocket 会话丢失 | 前端重连后通过 HTTP 拉取最新状态补全 |
| 大量用户同时在线 WebSocket 连接 | 当前为个人项目，用户数少，Spring 默认连接数足够 |
| 视频状态变化推送时序问题 | 推送只发最新状态，前端用 `videoId` 做幂等更新 |

## Migration Plan

零停机部署：
1. 新增 WebSocket 端点和配置（向后兼容）
2. REST API `/api/video/tasks` 保留不变
3. 前端同时支持 WebSocket 和轮询，WebSocket 优先
4. 旧客户端继续使用轮询不受影响

## Open Questions

无。
