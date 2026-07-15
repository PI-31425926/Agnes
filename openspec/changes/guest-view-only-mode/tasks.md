## 1. 前端 — GuestView 改造

- [x] 1.1 将 GuestView 简化为引导入口页（展示功能列表 + 注册登录引导）
- [x] 1.2 页面顶部保留"游客模式"标识和登录链接
- [x] 1.3 移除原有的游客聊天功能和 TTS 代码
- [x] 1.4 在 localStorage 中设置 `isGuest: "true"` 标志

## 2. 前端 — 路由守卫调整

- [x] 2.1 允许游客访问 `/`（MainView）查看所有功能 Tab
- [x] 2.2 游客访问 `/admin` 仍重定向到 `/login`
- [x] 2.3 登录成功后清除 `isGuest` 标志

## 3. 前端 — MainView 操作拦截

- [x] 3.1 添加 `isGuest` computed 和 `requireAuth()` 拦截函数
- [x] 3.2 在 sendChat() 中拦截（发送消息）
- [x] 3.3 在 generateText2img() 中拦截（文生图）
- [x] 3.4 在 generateImg2img() 中拦截（图生图）
- [x] 3.5 在 submitVideoTask() 中拦截（视频生成）
- [x] 3.6 在 createNewConversation() 中拦截（新建对话）
- [x] 3.7 在 deleteCurrentConversation() 中拦截（删除对话）
- [x] 3.8 在 deleteVideoTask() 中拦截（删除视频任务）
- [x] 3.9 在 handleFileUpload() / clearUploadedFile() 中拦截（文件上传）
- [x] 3.10 在 selectImageFile() / handleImageUpload() 中拦截（图片上传）
- [x] 3.11 在 handleVideoImageUpload() / handleVideoKeyframeUpload() 中拦截（视频图片上传）

## 4. 后端 — 端点标记

- [x] 4.1 在 GuestController.chat() 方法上添加 `@Deprecated` 注解和注释
- [x] 4.2 确认端点逻辑不变（向后兼容）

## 5. 后端 — 移除游客日志

- [x] 5.1 移除 `AgnesService.chatWithApiKey()` 中 `finally` 块的 `logService.log()` 调用
- [x] 5.2 在 `chatWithApiKey()` 方法上添加 `@Deprecated` 注解和注释说明

## 6. 验证

- [x] 6.1 游客访问 `/guest` 看到引导页，包含功能列表和登录按钮
- [x] 6.2 游客访问 `/` 能看到 MainView 所有 Tab（聊天/文生图/图生图/视频）
- [x] 6.3 游客在任何功能按钮上点击时弹出"暂不可用，请先注册或登录"提示
- [x] 6.4 游客访问 `/admin` 被重定向到 `/login`
- [x] 6.5 游客注册/登录后 `isGuest` 标志清除，所有功能恢复正常
- [x] 6.6 后端 `/api/guest/chat` 端点仍可正常调用（第三方兼容）
- [x] 6.7 确认 `AgnesService.chatWithApiKey()` 中不再有日志记录调用
- [x] 6.8 确认 `operation_logs` 表中无新增游客聊天日志
