## Why

当前游客模式（GuestView）登录后可以直接使用聊天功能，消耗注册用户提供的 API Key，存在滥用风险。需要将游客模式改为"仅浏览"状态：用户可以自由查看所有页面和 UI，但任何操作（聊天、生成图片/视频、管理等）都会拦截并提示注册/登录。

## What Changes

- 移除 GuestView 的聊天功能（原 `/api/guest/chat` 端点保留但不被前端调用）
- 在 GuestView 中添加"操作拦截器"：所有按钮和表单提交时检查游客模式标志，拦截并弹出注册/登录提示
- 保留游客模式的页面导航能力（可以查看 MainView、AdminView 等页面的 UI 结构）
- 后端保留 `/api/guest/chat` 端点但标记为 deprecated（前端不再使用）

## Capabilities

### New Capabilities
- `guest-view-only`: 游客模式仅浏览能力 — 游客可以查看所有页面 UI，但操作被拦截并引导注册/登录

### Modified Capabilities
<!-- None — no existing spec-level requirements are changing -->

## Impact

- **前端**: GuestView.vue（移除聊天功能，添加操作拦截）、router/index.js（游客路由守卫调整）、LoginView.vue（可能需要调整游客入口）
- **后端**: GuestController.java（端点标记 deprecated，功能保留）、GuestView 不再调用 `/api/guest/chat`
- **API**: 无 Breaking Change — 游客 API 端点保留，仅前端停止调用
