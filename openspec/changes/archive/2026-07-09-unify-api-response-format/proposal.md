## Why

后端控制器返回格式不一致：VideoController 使用 `ApiResponse<T>` 封装，ImageController/ChatController/AuthController 直接返回裸 DTO/Map/String。前端 `unwrapResponse()` 用 hack 方式兼容两种格式，脆弱且易出错。统一响应格式可从根源消除前端解包的不确定性。

## What Changes

- **后端统一响应格式**：所有 REST 端点返回 `ApiResponse<T>` 封装（`{ code, message, data }`）
- **新增全局 ResponseBodyAdvice**：自动包装非 `ApiResponse` 的返回值，无需修改每个 controller 方法
- **前端 Axios 拦截器**：自动提取 `response.data.data`，移除 `unwrapResponse` hack
- **前端统一数据访问**：所有接口调用直接使用解包后的 data，不再需要条件判断

## Capabilities

### Modified Capabilities

- `chat-context-management`: 历史接口返回格式从裸数组改为 `ApiResponse<List>`
- `video-websocket-push`: 视频列表接口返回格式从 `ApiResponse<List>` 保持不变（已统一）
- 新增 `api-response-consistency`: 统一的 API 响应格式规范

## Impact

**新增**: `ApiResponseBodyAdvice.java`（全局自动封装）、`api/request.js`（Axios 拦截器重构）
**修改**: `ImageController.java`、`ChatController.java`、`AuthController.java`、`FileUploadController.java`、`AdminController.java`（返回 ApiResponse 封装）
**修改**: `MainView.vue`（移除 unwrapResponse，改用 axios 拦截器自动解包）
**不变**: `VideoController.java`（已使用 ApiResponse）、`VideoWebSocketHandler.java`
