## Context

Agnes AI 平台的前端基于 Vue 3 + Vue Router 4 构建，采用赛博朋克暗色主题。当前系统已有完整的认证体系和路由守卫机制：

- 用户登录成功后，Token 和角色信息存储在 `localStorage` 中（`token` 和 `role` 字段）
- 路由守卫在 `frontend/src/router/index.js` 中实现，通过 `localStorage.getItem('role')` 读取角色
- `/admin` 路由已配置 `meta.role: 'ADMIN'` 守卫，非管理员访问会被重定向到首页
- 主页面 `MainView.vue` 的头部导航栏已有对话、文生图、图生图、视频四个标签和退出按钮

**约束**: 不修改后端代码，不引入新的依赖，保持现有 UI 风格一致。

## Goals / Non-Goals

**Goals:**
- 在主页面的导航栏中为管理员用户提供一个直观的入口跳转到管理面板
- 仅对角色为 ADMIN 的用户显示该入口
- 保持与现有 UI 风格一致的视觉设计

**Non-Goals:**
- 不修改后端 API 或数据库结构
- 不改变现有路由守卫的逻辑
- 不添加新的认证或授权机制
- 不修改其他页面的导航结构

## Decisions

### Decision 1: 在 MainView 头部导航栏右侧添加按钮
- **选择**: 在现有退出按钮旁边添加"管理面板"按钮
- **理由**: 
  - MainView 是管理员日常使用的页面，在此处添加入口符合用户习惯
  - 利用现有的导航栏布局，无需新增页面或路由
  - 按钮位置靠近退出按钮，语义上属于"高级操作"
- **备选方案**: 
  - 在侧边栏添加（当前无侧边栏，需新建组件，过重）
  - 在用户头像/下拉菜单中添加（当前无此功能，需新建）

### Decision 2: 使用 localStorage 中的 role 字段判断权限
- **选择**: 沿用现有的 `localStorage.getItem('role')` 判断逻辑
- **理由**: 
  - 登录时已存储角色信息，无需额外 API 调用
  - 与路由守卫使用相同的数据源，保持一致性
  - 性能开销为零
- **备选方案**: 
  - 从后端 API 获取用户信息（增加网络请求，不必要）
  - 创建全局 Pinia store（引入新状态管理，过重）

### Decision 3: 使用 computed 属性控制按钮显示
- **选择**: 在 Vue 组件中使用 `computed` 属性判断管理员身份
- **理由**: 
  - Vue 响应式系统自动处理，无需手动监听
  - 代码简洁，易于维护
  - 与现有 `currentTab` 等 computed 用法一致
- **备选方案**: 
  - 使用 `ref` + `onMounted` 初始化（每次需要重新读取）
  - 使用 `watch` 监听 localStorage 变化（过度设计）

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| 用户手动修改 localStorage 中的 role 字段绕过权限检查 | 路由守卫已在 `/admin` 路由上校验，按钮只是快捷入口，真正的权限校验在后端和路由层 |
| 管理员登出后按钮仍短暂显示 | 登出时清除 localStorage 所有字段，Vue 响应式系统会自动更新视图 |
| 未来角色体系扩展（如 SUPER_ADMIN） | 当前判断条件为 `role === 'ADMIN'`，如需支持多级管理员，改为 `['ADMIN', 'SUPER_ADMIN'].includes(role)` 即可 |