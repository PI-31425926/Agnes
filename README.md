# Agnes AI Platform

多模态 AI 应用平台 — 集成对话、图像生成、视频生成、音乐生成与可视化工作流编排，对接 Agnes AI 后端服务。

## 功能概览

| 模块 | 功能 |
|------|------|
| **对话** | 流式 SSE 响应、文档解析注入上下文、图像视觉理解、TTS 自动朗读、多轮会话管理 |
| **文生图** | 文本描述生成图片，支持 1024x768 / 768x1024 / 1024x1024 分辨率 |
| **图生图** | 上传底图 + 文字描述，生成修改后的图片 |
| **视频生成** | 三种模式：文生视频 / 图生视频 / 关键帧动画，异步任务队列 + WebSocket 实时推送 |
| **音乐生成** | 四种风格：燃歌/伤感/仙歌/MIDI，支持简谱输入、参数调节（温度/长度/BPM/调性/乐器），输出 Base64 MIDI 下载 |
| **工作流编排** | 可视化 DAG 编辑器，拓扑排序执行，8 种节点类型（聊天、提示词优化、文生图、图生图、图像理解、文生视频、图生视频、关键帧动画），自动变量绑定 |
| **提示词优化** | 按模态分类（文生图/图生图/文生视频/图生视频/关键帧动画），AI 辅助扩写 |
| **认证鉴权** | 手机号 + API Key 注册/登录，AES-GCM 加密存储用户密钥，Sa-Token 会话管理，管理员角色控制 |
| **游客模式** | 仅浏览，可访问主页但操作需登录 |
| **管理后台** | 用户管理、操作日志分页查询 |

## 技术栈

**后端**

- Java 17 + Spring Boot 3.x
- Sa-Token 认证鉴权
- Spring Data JPA + MySQL 8
- Spring Data Redis（会话历史、缓存、视频任务队列、Lua 配额控制）
- Apache Tika（文档解析：TXT / DOC / DOCX / PDF / XLS / XLSX）
- 阿里云 OSS（图片/视频文件存储）
- WebSocket（视频进度推送、工作流实时事件）
- Spring WebSocket + SSE（流式对话）

**前端**

- Vue 3.5 + Composition API (`<script setup>`)
- Vue Router 4.6
- Axios + Fetch（SSE 流式）
- Vue Flow（DAG 工作流可视化编辑器）
- Pinia（用户状态管理）
- Vite 5.4

## 项目结构

```
Agnes/
├── backend/                              # Spring Boot 后端
│   ├── src/main/java/com/bilibili/
│   │   ├── controller/                   # 12 个 REST 控制器
│   │   ├── service/                      # 业务逻辑层
│   │   │   └── workflow/                 # 工作流引擎（DAG 执行器 + 8 种节点）
│   │   ├── handler/                      # WebSocket 处理器
│   │   ├── config/                       # Sa-Token / Redis / 异步 / CORS 配置
│   │   ├── mapper/                       # JPA Repository 接口
│   │   ├── pojo/                         # Entity / DTO
│   │   ├── common/context/               # 用户上下文 ThreadLocal
│   │   └── utils/                        # AES 加密 / 视频任务管理 / 轮询调度
│   └── src/main/resources/
│       ├── application.yml               # 主配置文件
│       └── static/                       # 前端构建产物（生产模式）
├── frontend/                             # Vue 3 前端
│   ├── src/
│   │   ├── views/                        # MainView / LoginView / GuestView / AdminView / WorkflowView
│   │   ├── components/workflow/          # 工作流节点组件（调色板、节点类型）
│   │   ├── composables/                  # useWorkflowEditor / useWorkflowExecution / usePromptRefine
│   │   ├── api/                          # Axios 实例 + 模块化 API 调用
│   │   ├── stores/                       # Pinia 用户状态
│   │   ├── router/                       # 路由定义 + 鉴权守卫
│   │   └── assets/                       # 样式（赛博朋克暗色主题）
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
# 编辑 src/main/resources/application.yml 配置数据库、Redis 和 Agnes API 地址
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 前端开发模式

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，Vite 代理将 `/api` 请求转发到后端。

### 生产构建

```bash
cd frontend
npm run build
# 产物自动输出到 backend/src/main/resources/static/
cd ../backend
mvn package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

## 路由说明

| 路径 | 页面 | 鉴权 |
|------|------|------|
| `/` | 主应用（对话/生图/视频） | 需要 Token |
| `/login` | 登录/注册 | 无需 Token |
| `/guest` | 游客模式（仅浏览） | 无需 Token |
| `/admin` | 管理后台 | 需要 Token + ADMIN 角色 |
| `/workflow` | 工作流编辑器 | 需要 Token |

## API 接口

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册（手机号 + API Key） |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 当前用户信息 |

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 同步对话 |
| POST | `/api/chat/stream` | 流式对话 (SSE) |
| GET | `/api/chat/history` | 对话历史 |
| POST | `/api/chat/upload` | 上传文档（Tika 解析） |
| DELETE | `/api/chat/upload` | 清除已上传文档 |

### 图像

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/image` | 文生图 |
| POST | `/api/image/to-image` | 图生图 |
| GET | `/api/image/history` | 图片历史 |
| POST | `/api/image/upload` | 上传图片（OSS） |

### 视频

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/video/generate` | 创建视频生成任务 |
| GET | `/api/video/tasks` | 查询任务列表 |
| DELETE | `/api/video/tasks/{videoId}` | 删除任务 |
| POST | `/api/video/upload-image` | 上传单张参考图 |
| POST | `/api/video/upload-images` | 批量上传关键帧（最多 10 张） |

### 工作流

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/workflows` | 创建工作流 |
| GET | `/api/workflows` | 查询用户工作流列表 |
| GET | `/api/workflows/{id}` | 获取工作流详情 |
| PUT | `/api/workflows/{id}` | 更新工作流 |
| DELETE | `/api/workflows/{id}` | 删除工作流 |
| POST | `/api/workflows/{id}/execute` | 执行工作流 |
| POST | `/api/workflows/{id}/execute?nodeId=X` | 单节点调试模式 |
| GET | `/api/workflows/executions/{executionId}` | 查询执行记录 |
| POST | `/api/workflows/{id}/stop` | 停止工作流（待实现） |

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/conversations` | 会话列表 |
| POST | `/api/conversations` | 创建会话 |
| PUT | `/api/conversations/{id}/title` | 修改会话标题 |
| PUT | `/api/conversations/{id}/auto-title` | 自动提取标题 |
| DELETE | `/api/conversations/{id}` | 删除会话 |

### 提示词优化

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/prompts/refine` | AI 辅助优化提示词 |

### 音乐

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/music/generate` | 生成音乐（返回简谱文本 + Base64 MIDI） |

### 管理后台

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users` | 用户列表 |
| DELETE | `/api/admin/users/{id}` | 删除用户 |
| GET | `/api/admin/logs` | 操作日志（分页） |

### WebSocket

| 路径 | 说明 |
|------|------|
| `/api/ws/video` | 视频任务进度推送 |
| `/api/ws/workflow` | 工作流执行事件推送 |

## 配置说明

核心配置在 `backend/src/main/resources/application.yml`：

| 配置项 | 说明 |
|--------|------|
| `agnes.api-url` | Agnes AI 对话 API 地址 |
| `agnes.image-api-url` | 图像生成 API 地址 |
| `agnes.video-api-url` | 视频生成 API 地址 |
| `agnes.video-status-url` | 视频状态查询 API 地址 |
| `music.api-url` | 音乐生成服务地址 |
| `music.timeout` | 音乐生成超时时间（毫秒） |
| `spring.datasource` | MySQL 连接信息 |
| `spring.data.redis` | Redis 连接信息 |
| `oss.*` | 阿里云 OSS 配置（endpoint / bucket / access-key） |
| `aes.secret` | AES 加密密钥（用户 API Key 加密存储） |
| `sa-token.*` | Sa-Token 会话配置（有效期、签名等） |

## 业务规则

| 规则 | 约束 |
|------|------|
| 视频帧数 | 必须符合 8n+1 规则（81/121/241/409），最大 441 |
| 视频并发任务 | 每人最多 5 个（Redis Lua 原子校验） |
| 视频轮询间隔 | 20 秒，最长 60 分钟 |
| 对话历史 | 滑动窗口保留最近 20 条消息，30 分钟不活动过期 |
| 图片历史 | 最近 10 条，60 分钟 TTL |
| 文档上传 | 最大 10MB，Tika 解析后存 Redis 30 分钟 |
| 图片上传 | 最大 5MB，支持 jpg/png/webp |
| 关键帧图片 | 最多 10 张 |
| 会话标题 | 自动提取首条消息前 20 字符 |
| 音乐生成 | MIDI 模式忽略输入简谱文本 |

## 安全说明

- 用户 API Key 使用 AES-GCM 加密存储，支持旧格式自动迁移
- Sa-Token 会话管理，默认 24 小时有效期
- 管理员接口受 `@SaCheckRole("ADMIN")` 保护
- **生产环境务必修改 `aes.secret`、数据库密码、OSS AccessKey 等敏感配置**
