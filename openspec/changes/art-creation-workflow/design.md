## Context

Agnes 当前是一个多标签页 AI 应用（Chat / 文生图 / 图生图 / 视频生成），所有功能相互独立，用户需要在不同 Tab 间手动搬运结果。项目定位即将转变为"AI 创作平台"，核心交互是从拖拽工作流将多个 AI 能力串联成创作流水线。

当前技术栈：
- 前端：Vue 3 + Vite，无组件库，无状态管理，无工作流相关库
- 后端：Spring Boot 3.x + Java 17，RestTemplate 调外部 API，JPA + MySQL，Redis 缓存，Sa-Token 鉴权
- 外部 API：apihub.agnes-ai.com（OpenAI 兼容 chat、images/generations、videos）

## Goals / Non-Goals

**Goals:**
- 前端可视化工作流编辑器，支持拖拽节点、连线、配置参数
- 后端工作流执行引擎，解析 DAG 并按拓扑序执行节点
- 7 类 AI 节点的统一执行器接口（LLM 优化、文生文、文生图、图生图、图片理解、文生视频、图生视频、关键帧）
- 节点间通过变量引用传递数据（`${nodeId.field}` 语法）
- WebSocket 实时推送执行进度
- 工作流定义持久化到 MySQL，执行状态持久化到 MySQL + Redis

**Non-Goals:**
- 不涉及 Langchain4j 引入（确定性管道，不需要 LLM 智能路由）
- 不涉及 RAG / 向量检索 / Agent 系统
- 不重构现有的独立 Tab 功能（Chat / Image / Video API 保持不变）
- 不涉及条件分支、循环、子工作流等高级编排（v1 仅支持线性 + 扇出并行）

## Decisions

### D1: 前端使用 Vue Flow (@vue-flow/core)

**选择 Vue Flow 而非 LogicFlow / X6 / 手写。**

理由：
- 原生 Vue 3 组件，与项目技术栈零摩擦
- 轻量（~15KB gzipped），无重型框架依赖
- 内置拖拽、连线、节点自定义、视口缩放，满足 v1 需求
- LogicFlow 偏流程图风格，节点样式不够灵活；X6 太重且非 Vue 原生
- 手写 SVG/Canvas 工作量不可控，且已有成熟的 Vue Flow 可用

### D2: 后端工作流执行器采用同步阻塞 + 异步视频轮询混合模式

**理由：**
- LLM / 图片生成类节点响应快（秒级），适合同步执行，直接返回结果
- 视频生成类节点耗时久（分钟级），不适合同步阻塞。复用现有 VideoTaskManager + VideoPollingScheduler 模式：提交任务后立即返回 PENDING，后台轮询更新 Redis，WebSocket 推送进度
- 这样复用现有视频基础设施，避免重复建设

### D3: 工作流执行使用单线程顺序调度，不引入 Camunda / Temporal

**理由：**
- v1 的工作流规模较小（通常 < 20 个节点），不需要分布式工作流引擎的重量级特性
- Spring 内置的 `@Async` + `CompletableFuture` 足以满足线性执行 + 扇出并发的需求
- 引入 Camunda/Temporal 会增加运维复杂度（需要独立的 worker 部署），对于当前规模过度设计
- 如果未来工作流规模增长到需要分布式调度，可以平滑迁移

### D4: 变量解析在执行时动态进行，不在保存时静态校验

**理由：**
- 用户可能在编辑过程中尚未连接所有节点，保存时校验会阻碍迭代
- 执行时解析可以在错误信息中精确定位哪个节点的哪个变量引用失效
- 实现简单：ExecutionContext 维护一个 Map<String, Object>，节点执行前遍历配置中的 `${...}` 引用并替换

### D5: 数据库设计 — workflows 表存储 JSON，不拆分成规范化表

**理由：**
- 工作流结构高度灵活（任意节点类型、任意连接关系），关系型表难以优雅表达
- MySQL 的 JSON 类型足够支持 CRUD 和全文搜索需求
- 前端保存/加载直接读写 JSON，无需转换层
- 执行记录拆分成 workflow_executions 和 workflow_execution_nodes 表（结构化查询执行历史）

## Risks / Trade-offs

### Risk: Vue Flow 自定义节点样式成本高
每个节点类型需要自定义外观（图标、颜色、输入/输出手柄）。
→ **Mitigation**: 设计一套通用节点模板（圆角矩形 + 类型图标 + 输入输出手柄），7 种节点共享同一套视觉基础，仅通过颜色和图标区分。参考 Vue Flow 官方示例的 CustomNode 模式。

### Risk: 视频节点异步执行导致工作流执行状态不一致
如果工作流中有视频节点，执行引擎需要等待视频完成才能继续下游节点，但视频可能需要几分钟。
→ **Mitigation**: 视频节点标记为 `isAsync: true`，执行时提交任务后立即标记节点为 RUNNING 并返回。后台轮询更新节点状态。工作流整体状态在视频完成前保持 RUNNING。前端通过 WebSocket 实时查看进度。

### Risk: 并发执行时的上下文隔离
多个用户同时执行工作流时，ExecutionContext 不能共享。
→ **Mitigation**: ExecutionContext 在每次执行开始时创建，执行结束后销毁。使用 `new()` 而非单例。视频轮询通过 Redis 共享执行状态（executionId 作为 key），每个执行实例独立。

### Risk: 变量引用循环导致死锁
如果用户创建了 A → B → A 的循环连接，拓扑排序会检测到环并拒绝执行。
→ **Mitigation**: 执行前运行 Kahn 算法进行拓扑排序，检测到环时返回错误 "Workflow contains circular dependencies"。这在 spec 中未显式要求，但属于必要的防御性校验。

## Migration Plan

分三个阶段渐进式交付，每个阶段均可独立验证：

**Phase 1 — 后端引擎（无前端）：**
- 完成数据模型、执行器接口、执行引擎
- 通过 API 直接提交 JSON 工作流并执行
- 验证：用 Postman/curl 创建一个简单工作流并执行

**Phase 2 — 前端编辑器（无执行）：**
- 完成 Vue Flow 画布、节点拖拽、连线、配置面板
- 保存/加载工作流定义到后端
- 验证：可以在画布上编排工作流并保存到数据库

**Phase 3 — 前后端打通：**
- 前端 "Run" 按钮触发后端执行
- WebSocket 推送进度到前端画布
- 节点状态高亮、输出预览
- 验证：端到端运行一个完整创作流水线

**回滚策略：**
- 工作流功能是独立模块，不影响现有 Chat/Image/Video 功能
- 如果遇到问题，只需禁用 `/api/workflows/*` 路由和前端 WorkflowView 路由即可
- 数据库新增的表不影响现有表结构

## Open Questions

1. **是否需要工作流模板市场？** v1 不做，预留 `templates` 表结构扩展空间
2. **节点执行超时时间是否可配？** v1 使用默认值（LLM 30s，图片 60s，视频 600s），后续可通过配置文件调整
3. **是否需要节点级别的错误重试？** v1 不做自动重试，失败后用户手动修正输入重新执行
4. **前端是否需要离线编辑（不保存也能拖拽）？** Vue Flow 天然支持客户端编辑，保存时才调 API，天然支持离线编辑
