# Agnes AI Platform

一个基于 Vue 3 + Spring Boot 的多模态 AI 应用平台，集成对话、文生图、图生图和视频生成能力，对接 Agnes AI 后端服务。

## 技术栈

**后端**
- Java 17 + Spring Boot 4.1
- Spring Security + JWT 认证
- MySQL 8（用户、会话、操作日志持久化）
- Redis（会话历史、缓存）
- Apache Tika（文档解析）
- Lombok

**前端**
- Vue 3 + Vite
- Vue Router
- Axios

## 功能模块

| 模块 | 说明 |
|------|------|
| 对话 | 流式 SSE 响应、TTS 自动朗读、历史记录、文件上传（TXT/DOCX/PDF/XLSX） |
| 文生图 | 文本描述生成图片，支持多种分辨率 |
| 图生图 | 上传底图 + 描述，生成修改后的图片 |
| 视频 | 异步任务队列、进度轮询、浏览器通知 |
| 认证 | 手机号 + API Key 注册/登录，JWT Token 鉴权 |

## 项目结构

```
Agnes/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/bilibili/
│   │   ├── BackendApplication.java
│   │   ├── config/          # 安全、Redis、异步配置
│   │   ├── controller/      # 5 个 REST 控制器
│   │   ├── filter/          # JWT 过滤器
│   │   ├── common/context/  # 用户上下文 ThreadLocal
│   │   ├── mapper/          # JPA Repository
│   │   ├── pojo/            # Entity / DTO
│   │   ├── service/         # 业务逻辑
│   │   └── utils/           # AES、JWT、视频任务管理
│   └── src/main/resources/
│       ├── application.yml  # 主配置
│       └── static/          # 前端构建产物
├── frontend/                # Vue 3 前端
│   ├── src/
│   │   ├── views/           # LoginView / MainView
│   │   ├── router/          # 路由 + 守卫
│   │   └── assets/          # 样式 & 图标
│   └── package.json
```

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8
- Redis 7+

### 后端

```bash
cd backend
# 修改 application.yml 中的数据库和 Redis 连接信息
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 前端开发模式

```bash
cd frontend
npm install
npm run dev
```

Vite 代理将 `/api` 请求转发到后端。

### 前端构建

```bash
npm run build
# 产物复制到 backend/src/main/resources/static/
```

## 配置说明

核心配置在 `backend/src/main/resources/application.yml`：

- **agnes.api-url** — Agnes AI 对话 API 地址
- **agnes.image-api-url** — 图像生成 API
- **agnes.video-api-url / video-status-url** — 视频生成 API
- **spring.datasource** — MySQL 连接
- **spring.data.redis** — Redis 连接
- **jwt.secret / aes.secret** — 密钥（生产环境务必替换）

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 注册 |
| POST | /api/auth/login | 登录 |
| POST | /api/chat | 普通对话 |
| POST | /api/chat/stream | 流式对话 (SSE) |
| GET | /api/chat/history | 对话历史 |
| POST | /api/image | 文生图 |
| POST | /api/image/to-image | 图生图 |
| GET | /api/image/history | 图片历史 |
| POST | /api/video/generate | 创建视频任务 |
| GET | /api/video/tasks | 查询任务列表 |
| DELETE | /api/video/tasks/{videoId} | 删除任务 |

## 安全说明

本项目使用 AES 加密存储用户 API Key，JWT 进行认证。密钥配置在 `application.yml` 中，**生产环境请务必修改 `jwt.secret` 和 `aes.secret`**。