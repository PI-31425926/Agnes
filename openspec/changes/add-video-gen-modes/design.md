## Context

项目已有文生视频功能：前端 MainView.vue 的视频 Tab 提供 prompt + 参数输入，后端 `VideoController.generateVideo` 调用 `AgnesVideoService.createVideoTask` 向 Agnes API 发起请求，通过 WebSocket 推送任务进度。图片上传能力已通过 `AliyunOssService` 实现（上传到阿里云 OSS 返回 CNAME 公网 URL）。

当前限制：
- `AgnesVideoCreateRequest` 只有 model/prompt/width/height/numFrames/frameRate 字段，不支持 image 和 extra_body
- `VideoGenerationRequest` 同样缺少图片和模式字段
- 前端视频面板没有图片上传和模式选择 UI

## Goals / Non-Goals

**Goals:**
- 支持三种视频生成模式：文生视频（ti2vid）、图生视频（i2vid）、关键帧动画（keyframes）
- 复用现有 AliyunOssService 上传图片获取公网 URL
- 复用 WebSocket 任务推送机制，前端任务列表兼容新模式
- 向后兼容：现有文生视频功能不受影响

**Non-Goals:**
- 不修改 Agnes Video V2.0 API 的模型名称（固定使用 agnes-video-v2.0）
- 不做视频编辑/裁剪等后处理能力
- 不引入新的外部依赖（复用阿里云 OSS SDK）

## Decisions

### Decision 1: 复用现有 upload 流程，新增视频专用图片上传端点
- 聊天用的图片上传（`/api/image/upload`）设计为单图且走聊天流程
- 视频场景需要支持多图（关键帧），因此新增 `/api/video/upload-images` 端点，支持多文件上传，返回 URL 数组
- 底层复用 `AliyunOssService.uploadImage()`，仅调整 objectName 前缀为 `video-images/`

### Decision 2: 后端新增方法而非修改现有方法签名
- `AgnesVideoService` 新增 `createVideoTaskWithImage()` 和 `createVideoTaskWithKeyframes()` 两个重载方法
- 原有 `createVideoTask()` 保持不变，保证向后兼容
- 三个方法内部通过统一的 `buildVideoRequestBody()` 私有方法组装 JSON

### Decision 3: 前端模式选择通过折叠区内的下拉框实现
- 在现有 "新建视频任务" 折叠区内新增模式选择器（文生视频/图生视频/关键帧动画）
- 选择"图生视频"时显示单图上传区域，选择"关键帧动画"时显示多图上传区域
- 图片上传后预览缩略图，支持删除单张
- 提交时将模式 + 图片 URL(s) 一并发送到后端

### Decision 4: 使用 extra_body 传递关键帧参数
- 图生视频：`image` 字段传单个 URL 字符串
- 关键帧：`extra_body` 对象中包含 `image`（URL 数组）和 `mode: "keyframes"`

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| 多图上传时 OSS 请求并发导致延迟 | 后端串行上传，前端显示逐张上传进度 |
| 关键帧图片数量过多导致 API 请求体过大 | 限制最多 10 张关键帧，单张 ≤ 5MB |
| 新模式参数校验遗漏导致 API 报错 | 前后端双重校验：前端即时校验，后端兜底返回 400 |
| 现有文生视频用户不受影响但增加 UI 复杂度 | 模式默认选中"文生视频"，交互与非新模式一致 |

## Migration Plan

无需数据库迁移。所有变更为代码级增量：
1. 后端新增 DTO 字段（Jackson 反序列化对未知字段兼容）
2. 后端新增 Service 方法和 Controller 端点
3. 前端渐进式增强：新模式默认隐藏，用户主动选择后才展开

## Open Questions

无。
