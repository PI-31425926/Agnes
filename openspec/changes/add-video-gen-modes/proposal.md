## Why

当前视频生成功能仅支持"文生视频"（text-to-video），而 Agnes Video V2.0 API 已支持图生视频（image-to-video）和关键帧动画（keyframes）两种新模式。为满足用户更多样的视频创作需求，需要在现有基础上扩展这两种能力，并将上传的图片通过阿里云 OSS 转为公网 URL 作为输入源。

## What Changes

- **图生视频（Image-to-Video）**：用户上传一张图片 + 输入提示词，后端将图片 URL 通过 `image` 字段传给 Agnes API
- **关键帧动画（Keyframes）**：用户上传多张图片 + 输入提示词，后端将图片 URL 数组通过 `extra_body.image` 传给 Agnes API
- **视频生成模式选择器**：前端新增下拉选项，用户可在 "文生视频 / 图生视频 / 关键帧动画" 三种模式间切换
- **图片上传复用**：复用已有的 `AliyunOssService` 上传图片到阿里云，返回公网 URL 供视频接口使用
- **后端 DTO 扩展**：`AgnesVideoCreateRequest` 增加 `image`、`mode`、`extra_body` 字段
- **后端 Service 扩展**：`AgnesVideoService.createVideoTask` 支持传入图片 URL 和模式参数
- **后端 Controller 扩展**：`VideoController.generateVideo` 接收新模式参数并路由到不同 API 调用
- **前端视频面板重构**：新增图片上传区域（单图/多图）、模式选择器、图片预览与删除

## Capabilities

### New Capabilities
- `video-generation`: 视频生成核心能力，涵盖文生视频、图生视频、关键帧动画三种模式的 API 对接与任务管理

## Impact

- **Backend**: `AgnesVideoService.java`（扩展 createVideoTask 方法）、`AgnesVideoCreateRequest.java`（新增 image/mode/extra_body 字段）、`VideoController.java`（接收新模式参数）、`VideoGenerationRequest.java`（DTO 扩展）
- **Frontend**: `MainView.vue`（视频面板 UI 重构，新增模式选择和图片上传）
- **Existing**: 复用 `AliyunOssService` 图片上传能力，复用 WebSocket 任务推送机制，不影响现有文生视频功能
