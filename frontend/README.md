# Agnes AI Frontend

Agnes AI 平台的 Web 前端，基于 Vue 3 + Vite 构建，提供对话、图像生成和视频生成的统一界面。

## 功能

- 💬 **流式对话** — SSE 实时推送，TTS 自动朗读，历史记录加载
- 🎨 **文生图** — 文本描述生成图片，支持多种分辨率
- 🖼️ **图生图** — 上传底图 + 描述，生成修改后的图片
- 🎬 **视频生成** — 异步任务队列，进度轮询，浏览器通知
- 🔐 **认证** — 手机号 + API Key 注册/登录

## 技术栈

- Vue 3.5 + Composition API (`<script setup>`)
- Vue Router 4.6
- Axios 1.17
- Vite 5.4

## 项目结构

```
frontend/
├── public/
│   └── favicon.ico
├── src/
│   ├── assets/          # 静态资源
│   │   ├── base.css
│   │   ├── main.css
│   │   └── logo.svg
│   ├── components/      # 可复用组件
│   │   ├── HelloWorld.vue
│   │   └── icons/       # 社区图标集
│   ├── router/
│   │   └── index.js     # 路由定义 + 鉴权守卫
│   └── views/
│       ├── LoginView.vue  # 登录/注册页
│       └── MainView.vue   # 主应用（对话、生图、视频）
├── index.html
├── package.json
└── vite.config.js
```

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发模式

```bash
npm run dev
```

前端默认运行在 `http://localhost:5173`，通过 Vite 代理将 `/api` 请求转发到后端 `http://localhost:8080`。

### 构建

```bash
npm run build
```

产物输出到 `dist/` 目录，可部署到静态服务器或复制到后端 `backend/src/main/resources/static/` 由 Spring Boot 托管。

### 预览

```bash
npm run preview
```

## 路由

| 路径 | 页面 | 鉴权 |
|------|------|------|
| `/` | 主应用 | 需要 Token |
| `/login` | 登录/注册 | 无需 Token |

登录态通过 `localStorage.getItem('token')` 判断，未登录时访问首页会自动跳转至 `/login`。

## API 调用

前端通过 Axios 与后端通信，主要接口：

- `POST /api/auth/login` — 登录
- `POST /api/auth/register` — 注册
- `POST /api/chat/stream` — 流式对话 (原生 Fetch)
- `GET /api/chat/history` — 对话历史
- `POST /api/image` — 文生图
- `POST /api/image/to-image` — 图生图
- `GET /api/image/history` — 图片历史
- `POST /api/video/generate` — 创建视频任务
- `GET /api/video/tasks` — 任务列表
- `DELETE /api/video/tasks/:videoId` — 删除任务

## 样式主题

采用赛博朋克风格的暗色主题，主色调为青色 `#0ff`，带有动态网格背景和毛玻璃效果。