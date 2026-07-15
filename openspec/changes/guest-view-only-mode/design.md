## Context

当前 GuestView.vue 是一个完整的游客聊天页面，调用 `/api/guest/chat` 端点使用注册用户提供的 API Key 进行对话。随着平台使用量增长，游客模式被滥用消耗注册用户的 API 配额。需要将游客模式改为纯浏览模式，降低运营成本同时提升用户体验（游客可以先看界面再决定是否注册）。

## Goals / Non-Goals

**Goals:**
- 游客可以自由访问 MainView 查看所有功能 Tab（聊天/文生图/图生图/视频）
- 游客的任何功能性操作都被拦截，弹出提示引导注册/登录
- 游客访问 AdminView 仍需要登录（角色守卫不变）
- 保留后端 `/api/guest/chat` 端点（向后兼容，不破坏可能的第三方调用）
- 游客操作不记录日志

**Non-Goals:**
- 不实现游客账户体系
- 不改变已登录用户的任何行为

## Decisions

### 1. 游客模式标识方式
**决策**: 使用 localStorage 中的固定标志位 `isGuest: "true"` 标识游客模式，与 token 字段正交。

**理由**:
- 游客没有 token，需要一个明确的标志告诉各页面"当前是游客模式"
- 在 GuestView 加载时设置 `localStorage.setItem('isGuest', 'true')`，登录成功后清除

### 2. 游客可访问 MainView
**决策**: 修改路由守卫，允许游客访问 `/`（MainView），但不能访问 `/admin`。

**理由**:
- 游客需要看到完整的 UI 才能了解平台功能
- 功能拦截在 MainView 内部通过 `requireAuth()` 函数实现
- AdminView 仍需 ADMIN 角色，游客无法访问

### 3. 功能拦截策略
**决策**: 在 MainView 中定义 `isGuest` computed 和 `requireAuth()` 函数，在所有触发 API 调用的方法入口处检查。

**拦截的方法列表**:
- `sendChat()` — 发送消息
- `generateText2img()` — 文生图
- `generateImg2img()` — 图生图
- `submitVideoTask()` — 视频生成
- `createNewConversation()` / `deleteCurrentConversation()` — 对话管理
- `deleteVideoTask()` — 删除视频任务
- `selectImageFile()` / `handleImageUpload()` / `handleVideoImageUpload()` / `handleVideoKeyframeUpload()` — 所有图片上传
- `handleFileUpload()` / `clearUploadedFile()` — 文件上传

**拦截方式**: `alert('游客模式暂不可用，请先注册或登录后使用。')` + 直接 return

### 4. 游客日志处理
**决策**: 移除 `AgnesService.chatWithApiKey()` 中的 `logService.log()` 调用，游客操作不记录日志。

**理由**:
- 游客没有身份标识，日志审计价值低
- 游客聊天消耗的是注册用户的 API Key，记录日志无法追溯真实操作者
- 前端不再调用游客聊天，日志路径实际上不会触发，但移除死代码更干净
- 减少不必要的数据库写入（`operation_logs` 表异步插入）

### 5. 提示方式
**决策**: 使用浏览器原生 `alert()` ，不引入新的 UI 组件库。

**理由**:
- 项目当前没有组件库，引入成本高于收益
- 游客场景简单，alert 足够表达意图
- 与现有代码风格一致

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| 游客通过直接 API 调用绕过前端拦截 | 后端已有 Sa-Token 认证拦截器，未登录请求无法通过 `/api/**` |
| 游客频繁点击导致大量 alert 弹窗 | alert 是同步阻塞的，用户体验差但可以接受，后续可优化为 Toast |
| 后端 `/api/guest/chat` 仍有第三方调用被中断 | 端点保留不变，只是前端不调用了 |
| 游客日志残留死代码 | 移除 `chatWithApiKey()` 中的 `logService.log()` 调用 |

## Migration Plan

1. 修改 GuestView.vue：简化为引导入口页（展示功能列表 + 注册登录引导）
2. 修改 router/index.js：允许游客访问 `/`（MainView），但 `/admin` 仍需要登录
3. 修改 MainView.vue：添加 `isGuest` computed + `requireAuth()` 拦截函数，包裹所有操作
4. 修改 LoginView.vue：登录成功后清除 `isGuest` 标志
5. 在 GuestController 的 chat 方法上添加 `@Deprecated` 注解
6. 移除 `AgnesService.chatWithApiKey()` 中的日志记录调用并标记 `@Deprecated`
7. 测试：游客访问各页面、尝试操作、注册登录后恢复正常

## Open Questions

无。
