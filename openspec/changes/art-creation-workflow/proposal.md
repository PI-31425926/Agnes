## Why

当前 Agnes 平台的文生文、文生图、图生图、文生视频、关键帧动画等功能各自独立，用户需要在不同 Tab 之间手动复制粘贴结果，无法形成创作流水线。用户期望通过可视化拖拽编排的方式，将多个 AI 能力串联成工作流（如：提示词优化 → 文生图 → 图片理解 → 文生视频），实现从文字到视频的闭环创作。

## What Changes

- **新增可视化工作流编辑器**：基于 Vue Flow 的前端画布，支持节点拖拽、连线、配置参数
- **新增 7 种 AI 节点类型**：LLM 提示词优化、文生文、文生图、图生图、图片理解、文生视频、关键帧动画
- **新增工作流执行引擎**：后端解析工作流 DAG，按拓扑序执行各节点，节点间通过上下文传递数据
- **新增工作流持久化**：工作流定义存储 MySQL，执行状态存储 Redis
- **新增 WebSocket 进度推送**：实时推送各节点执行状态和中间结果
- **保留现有独立功能**：原有的 Chat、Image、Video 独立 Tab 和 API 不变，作为底层能力被工作流节点复用

## Capabilities

### New Capabilities

- `workflow-engine`: 工作流定义、执行引擎、节点调度、上下文数据传递
- `workflow-editor`: 前端可视化工作流画布、节点拖拽编排、参数配置面板
- `node-executors`: 7 种 AI 能力节点的统一执行器接口和具体实现

### Modified Capabilities

<!-- None — existing capabilities (chat, image, video) keep their current behavior and APIs -->

## Impact

- **前端**：新增 `@vue-flow/core` 依赖；新建 WorkflowView.vue 及大量节点/面板组件；现有 MainView.vue 不变
- **后端**：新增 WorkflowEngine、NodeExecutor 体系、Workflow/Execution 实体；复用现有 AgnesService/AgnesImageService/AgnesVideoService 作为底层能力
- **数据库**：新增 `workflows` 表、`workflow_executions` 表、`workflow_execution_nodes` 表
- **外部依赖**：新增 `@vue-flow/core`（前端）、无新增后端第三方库
- **API**：新增 `/api/workflows/*` 路由；现有 `/api/chat`、`/api/image`、`/api/video` 保持不变
