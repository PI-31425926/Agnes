## 1. 添加管理员身份计算属性

- [ ] 1.1 在 MainView.vue 的 `<script setup>` 中添加 isAdmin computed 属性，读取 localStorage.getItem('role') === 'ADMIN'

## 2. 添加管理面板导航按钮

- [ ] 2.1 在 MainView.vue 头部导航栏的退出按钮左侧添加"管理面板"按钮
- [ ] 2.2 使用 v-if="isAdmin" 控制按钮仅对管理员显示
- [ ] 2.3 按钮点击事件使用 router.push('/admin') 跳转到管理面板
- [ ] 2.4 按钮样式复用现有 tab-btn 样式类，保持视觉一致性

## 3. 验证

- [ ] 3.1 管理员登录后确认导航栏显示"管理面板"按钮
- [ ] 3.2 普通用户登录后确认不显示"管理面板"按钮
- [ ] 3.3 点击按钮后成功跳转到 /admin 页面