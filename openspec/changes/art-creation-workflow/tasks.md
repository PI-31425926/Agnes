## 1. 后端 — 数据模型与基础设施

- [x] 1.1 创建 Workflow 实体类 + WorkflowRepository（JPA 映射 workflows 表，包含 id、userId、name、definition JSON、createdAt、updatedAt）
- [x] 1.2 创建 WorkflowExecution 实体类 + Repository（映射 workflow_executions 表，包含 id、workflowId、status、result、createdAt、completedAt）
- [x] 1.3 创建 WorkflowExecutionNode 实体类 + Repository（映射 workflow_execution_nodes 表，包含 executionId、nodeId、nodeType、status、inputJson、outputJson、error、startedAt、completedAt）
- [x] 1.4 创建 NodeExecutor 接口和 ExecutionResult 类（定义 nodeType()、execute()、isAsync() 方法）
- [x] 1.5 创建 ExecutionContext 类（线程安全的 Map-based 变量存储，支持 get(nodeId.field) 语法）
- [x] 1.6 在 pom.xml 中添加 mysql-connector-j runtime 依赖确认（已有，无需新增）

## 2. 后端 — NodeExecutor 实现（7 种节点）

- [x] 2.1 创建 LlmRefineExecutor（复用 AgnesService 的 chat 能力，内置提示词优化 system prompt）
- [x] 2.2 创建 TextChatExecutor（复用 AgnesService，支持自由对话）
- [x] 2.3 创建 TextToImageExecutor（复用 AgnesImageService 的 generateImage 方法）
- [x] 2.4 创建 ImageToImageExecutor（复用 AgnesImageService 的 generateImageFromImage 方法）
- [x] 2.5 创建 ImageUnderstandExecutor（复用 AgnesService 的 vision 能力，传入图片 URL + 描述 prompt）
- [x] 2.6 创建 TextToVideoExecutor（复用 AgnesVideoService，提交任务 + 轮询 + WebSocket 推送）
- [x] 2.7 创建 ImageToVideoExecutor（复用 AgnesVideoService 的 i2vid 模式）
- [x] 2.8 创建 KeyframeAnimationExecutor（复用 AgnesVideoService 的 keyframes 模式）
- [x] 2.9 创建 ExecutorRegistry 类（自动扫描所有 NodeExecutor Bean 并按 nodeType() 注册）

## 3. 后端 — 工作流执行引擎

- [x] 3.1 创建 WorkflowEngine 核心类（接收 workflow JSON + ExecutionContext，执行整个工作流）
- [x] 3.2 实现 DAG 解析：从 JSON 中提取 nodes 和 edges，构建有向图
- [x] 3.3 实现拓扑排序（Kahn 算法），检测循环依赖
- [x] 3.4 实现变量解析器：遍历节点配置中的 `${nodeId.field}` 引用并替换为实际值
- [x] 3.5 实现同步节点执行（LLM / 图片类）：顺序执行，阻塞等待结果
- [x] 3.6 实现异步节点执行（视频类）：提交任务后立即返回 RUNNING，后台轮询更新状态
- [x] 3.7 实现扇出并行：当多个下游节点依赖同一个上游节点时，上游完成后并行执行下游
- [x] 3.8 实现执行进度推送：通过 WebSocket 发送 node_started / node_completed / execution_completed 事件
- [x] 3.9 实现错误处理：节点失败时标记 FAILED，停止下游执行，记录错误信息

## 4. 后端 — Workflow REST API

- [x] 4.1 创建 WorkflowController（映射 /api/workflows/* 路由）
- [x] 4.2 实现 POST /api/workflows — 创建工作流（从前端 JSON 保存）
- [x] 4.3 实现 GET /api/workflows — 列出当前用户的所有工作流
- [x] 4.4 实现 GET /api/workflows/{id} — 获取单个工作流详情
- [x] 4.5 实现 PUT /api/workflows/{id} — 更新工作流
- [x] 4.6 实现 DELETE /api/workflows/{id} — 删除工作流
- [x] 4.7 实现 POST /api/workflows/{id}/execute — 触发工作流执行，返回 executionId
- [x] 4.8 实现 GET /api/workflows/executions/{executionId} — 查询执行状态和历史
- [x] 4.9 实现 POST /api/workflows/{id}/stop — 停止正在执行的工作流
- [x] 4.10 确保所有接口通过 Sa-Token 鉴权（复用现有 SaTokenConfig 拦截器）

## 5. 后端 — WebSocket 执行事件推送

- [x] 5.1 创建 WorkflowWebSocketHandler（复用现有 WebSocket 基础设施）
- [x] 5.2 实现执行进度事件推送（execution_started、node_started、node_completed、node_failed、execution_completed、execution_stopped）
- [x] 5.3 实现事件格式统一（type、executionId、workflowId、nodeId、status、timestamp、payload）
- [x] 5.4 在 WebSocketConfig 中注册新的 WebSocket 端点（如 /api/ws/workflow）

## 6. 前端 — Vue Flow 集成与基础画布

- [x] 6.1 在 frontend/package.json 中添加 @vue-flow/core (^1.18.x) 和 @vue-flow/background / @vue-flow/controls / @vue-flow/minimap 依赖
- [x] 6.2 创建 src/views/WorkflowView.vue（主视图，包含侧边栏节点面板 + 中央画布区域）
- [x] 6.3 创建 src/composables/useWorkflowEditor.js（封装画布状态：nodes、edges、selectedNode、viewport）
- [x] 6.4 创建 src/components/workflow/NodePalette.vue（侧边栏，按分类展示节点类型）
- [x] 6.5 创建 src/components/workflow/WorkflowCanvas.vue（Vue Flow 画布容器，配置背景网格、缩放控件）
- [x] 6.6 创建 src/router/index.js 路由添加 `/workflow` 路径（requiresAuth: true）

## 7. 前端 — 自定义节点组件

- [x] 7.1 创建 src/components/workflow/nodes/BaseNode.vue（通用节点模板：圆角矩形 + 图标 + 标题 + 输入/输出手柄）
- [x] 7.2 创建 7 种节点类型的专用组件（TextInputNode、LlmRefineNode、TextToImageNode、ImageToImageNode、ImageUnderstandNode、TextToVideoNode、KeyframeNode），均继承 BaseNode 样式
- [x] 7.3 创建 src/components/workflow/nodes/NodeIcon.vue（节点类型图标组件，使用 Unicode/emoji 或内联 SVG）
- [x] 7.4 将自定义节点注册为 Vue Flow 的 CustomNode，绑定到对应的 type 属性

## 8. 前端 — 节点配置面板

- [x] 8.1 创建 src/components/workflow/NodeConfigPanel.vue（当节点被选中时显示的右侧面板）
- [x] 8.2 实现 LLM 节点配置表单（system prompt 文本框、temperature 滑块、maxTokens 输入框、模型下拉）
- [x] 8.3 实现图片生成节点配置表单（prompt 文本框、尺寸下拉、质量选项）
- [x] 8.4 实现视频生成节点配置表单（prompt 文本框、分辨率、帧率、帧数、图片上传区）
- [x] 8.5 实现变量引用绑定（配置字段支持输入 `${nodeId.field}` 格式的变量引用，面板中显示引用来源提示）
- [x] 8.6 实现配置保存（修改配置后实时更新到画布的 node.data 中）

## 9. 前端 — 工作流 CRUD 与保存加载

- [x] 9.1 创建 src/api/workflow.js（封装工作流相关的 API 调用：list/save/get/delete/execute）
- [x] 9.2 创建 src/components/workflow/WorkflowToolbar.vue（工具栏：保存、加载、运行、停止、导出按钮）
- [x] 9.3 实现保存工作流（序列化画布 JSON → POST /api/workflows）
- [x] 9.4 实现加载工作流（GET /api/workflows/{id} → 反序列化为 Vue Flow nodes/edges）
- [x] 9.5 实现工作流列表选择器（下拉或侧边栏列出已保存的工作流，点击加载）
- [x] 9.6 实现导出 JSON 文件（序列化 → Blob → download）

## 10. 前端 — 执行进度可视化

- [x] 10.1 创建 src/composables/useWorkflowExecution.js（封装 WebSocket 连接、事件监听、执行状态管理）
- [x] 10.2 实现 WebSocket 连接（连接到 /api/ws/workflow，携带 token 认证）
- [x] 10.3 实现节点状态高亮（running: 蓝色边框闪烁、completed: 绿色边框、failed: 红色边框）
- [x] 10.4 实现节点输出预览（hover 节点时 tooltip 显示输出摘要：文本结果、图片缩略图、视频链接）
- [x] 10.5 实现执行进度条（顶部显示整体进度：X/Y 节点完成）
- [x] 10.6 实现停止执行（点击停止按钮 → POST /api/workflows/{id}/stop）

## 11. 联调与端到端测试

- [ ] 11.1 测试线性工作流：Text Input → LLM Refine → Text to Image（完整端到端）
- [ ] 11.2 测试扇出工作流：Text Input 同时连接 LLM Refine 和 Image Understand（并行执行验证）
- [ ] 11.3 测试变量引用：LLM Refine 的输出正确传递给 Text to Image 的 prompt
- [ ] 11.4 测试视频工作流：Text to Video 异步执行，进度实时更新，完成后返回视频 URL
- [ ] 11.5 测试错误处理：无效节点配置、API 调用失败、循环依赖检测
- [ ] 11.6 测试并发：两个用户同时执行工作流，上下文隔离验证
- [ ] 11.7 测试现有功能不受影响：Chat / Image / Video 独立 Tab 仍然正常工作
