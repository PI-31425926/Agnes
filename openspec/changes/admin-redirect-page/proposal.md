## Why

当前管理员只能通过直接在地址栏输入 `/admin` 进入管理面板，缺乏便捷入口。普通用户在登录后也无法感知管理员功能的存在，而管理员在常规页面中也需要手动输入 URL 才能跳转到管理界面，体验不够直观。

## What Changes

- 在管理员登录后，主页面（`/`）的顶部导航栏中新增「管理面板」跳转入口
- 点击入口后自动跳转至 `/admin` 页面
- 仅对角色为 ADMIN 的用户显示该入口，普通用户不可见
- 保持现有管理员权限校验逻辑不变（路由守卫已在 `/admin` 路由上配置）

## Capabilities

### New Capabilities
- `admin-nav-entry`: 管理员导航入口 — 在主页面导航栏中为管理员用户提供跳转到管理面板的入口按钮

### Modified Capabilities
<!-- None: this is a purely additive feature, no existing spec requirements are changing -->

## Impact

- **前端**: 修改 `frontend/src/views/MainView.vue`，在头部导航区域新增管理员专属按钮
- **前端**: 可能需要修改 `frontend/src/router/index.js` 中的路由守卫逻辑（如果角色存储位置有变化）
- **无后端变更**: 仅前端 UI 层面的调整，不涉及 API 或后端逻辑
- **无 Breaking Change**: 对现有功能无影响，纯增量添加