## ADDED Requirements

### Requirement: 游客模式操作拦截
游客用户访问任何需要身份验证的功能时，系统 SHALL 拦截操作并提示注册/登录。

#### Scenario: 游客点击聊天发送按钮
- **WHEN** 游客用户在 GuestView 中输入消息并点击发送
- **THEN** 系统弹出提示"游客模式仅支持浏览，请先注册或登录后使用完整功能"，不发起任何 API 请求

#### Scenario: 游客尝试访问主功能页面
- **WHEN** 游客用户通过 URL 直接访问 `/`（MainView）或 `/admin`（AdminView）
- **THEN** 路由守卫检查游客模式标志，显示提示并停留在 GuestView 或跳转到登录页

### Requirement: 游客模式仅浏览 UI
游客用户可以正常渲染所有页面组件和 UI 布局，包括 MainView 的聊天/图片/视频 Tab、AdminView 的管理面板等。

#### Scenario: 游客打开 MainView
- **WHEN** 游客用户导航到主界面
- **THEN** 页面正常渲染，但所有功能按钮（发送消息、生成图片、生成视频等）显示为禁用状态或点击后弹出注册提示

#### Scenario: 游客打开 AdminView
- **WHEN** 游客用户导航到管理页面
- **THEN** 页面正常渲染管理面板 UI，但所有管理操作（删除用户、查看日志等）均被拦截

### Requirement: 游客模式 API 端点保留
后端 `/api/guest/chat` 端点 SHALL 保留但标记为 deprecated，不再被前端调用。

#### Scenario: 游客模式端点标记
- **WHEN** 开发者查看 GuestController.java
- **THEN** `POST /api/guest/chat` 方法上有 `@Deprecated` 注解或注释说明已弃用

### Requirement: 游客引导注册登录
游客模式下提供明显的注册/登录入口。

#### Scenario: 游客看到注册登录引导
- **WHEN** 游客用户首次尝试任何操作
- **THEN** 系统显示引导文案和按钮，点击后跳转到 `/login` 页面
