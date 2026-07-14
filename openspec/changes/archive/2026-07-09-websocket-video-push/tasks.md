## 1. Backend — WebSocket Configuration

- [x] 1.1 Create `WebSocketConfig.java` — register `/api/ws/video` endpoint with `TextWebSocketHandler`
- [x] 1.2 Create `VideoWebSocketHandler.java` — implement `afterConnectionEstablished`, `handleTextMessage`, `afterConnectionClosed`
- [x] 1.3 Implement token extraction from query parameter in `afterConnectionEstablished` — reject if invalid/missing
- [x] 1.4 Implement `Map<String, Set<WebSocketSession>>` to track sessions by userId (phone)

## 2. Backend — Push Integration with VideoPollingScheduler

- [x] 2.1 Inject `VideoWebSocketHandler` into `VideoPollingScheduler`
- [x] 2.2 In `pollVideoStatuses()`, detect status changes and call `pushToUser(userId, taskInfo, eventType)`
- [x] 2.3 Send `video_status` message for any status change
- [x] 2.4 Send `video_completed` message when status becomes `completed` with valid URL

## 3. Frontend — WebSocket Connection Management

- [x] 3.1 In `MainView.vue` video section, add WebSocket connection state refs (`wsConnected`, `wsSession`)
- [x] 3.2 Implement `connectVideoWebSocket()` — open WebSocket with token in query, register `onmessage` handler
- [x] 3.3 Implement `onmessage` handler — parse JSON, update `videoTasks` by `videoId` for `video_status`, display video player for `video_completed`
- [x] 3.4 Implement reconnect logic — 3s delay, max 5 retries, then fall back to HTTP polling

## 4. Frontend — Fallback Polling

- [x] 4.1 Refactor existing `fetchVideoTasks()` to be reusable as fallback
- [x] 4.2 In video tab watcher, start WebSocket connection first, schedule fallback polling only if WebSocket fails
- [x] 4.3 On successful WebSocket reconnection, clear fallback polling timer
- [x] 4.4 Clean up WebSocket connection and timers on tab switch away and component unmount
