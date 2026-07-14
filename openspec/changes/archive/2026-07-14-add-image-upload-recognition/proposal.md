## Why

当前对话系统仅支持纯文本聊天和基于 Apache Tika 的文档文件（txt/pdf/doc 等）文字提取。用户希望能够在聊天中直接上传图片，让 Agnes 2.0 Flash 视觉模型识别图片内容，提升交互体验和多模态能力。

## What Changes

- **前端**: 在聊天输入区域新增图片上传按钮，支持拖拽/粘贴/选择图片，显示图片预览和标签，**每次仅限一张图片**，单张 ≤1MB（超自动压缩），支持 jpg/png/webp 格式
- **后端**: 新增图片上传接口，接收图片后压缩（如需）并上传至阿里云 OSS，返回公网 URL；将图片 URL 嵌入聊天消息体发送给 Agnes API（vision 格式）
- **AgnesService 改造**: 消息构建逻辑支持 OpenAI Vision 格式的 content 数组（`[{type:"text"}, {type:"image_url", image_url:{url:"..."}}]`），兼容现有纯文本消息
- **配置**: 新增阿里云 OSS 配置项（endpoint、bucket、accessKeyId、accessKeySecret）

## Capabilities

### New Capabilities

- `image-upload`: 图片上传到阿里云 OSS 的能力，含压缩、上传、URL 返回
- `image-recognition`: 聊天对话中发送图片 URL 给 Agnes 2.0 Flash 进行视觉识别的能力

### Modified Capabilities

<!-- None — no existing spec-level requirements are changing -->

## Impact

| 区域 | 影响 |
|------|------|
| 后端 Controller | 新增 `ImageUploadController`；改造 `ChatController` 的 `ChatRequest` DTO 增加 `images` 字段 |
| 后端 Service | `AgnesService.chatStreamReal()` 消息构建改为支持 vision 格式；新增 `AliyunOssService` |
| 后端 Config | `application.yml` 新增 oss 配置段 |
| 后端依赖 | 新增 `aliyun-sdk-oss` Maven 依赖 |
| 前端 UI | `MainView.vue` 聊天区域新增图片上传按钮、预览、压缩（Canvas）、标签展示 |
| 前端 API | `chat.js` 新增 `uploadImage` 方法 |
| 前端 DTO | 发送消息的 JSON body 新增 `images: string[]` 字段 |
| 外部依赖 | 阿里云 OSS（北京 region，bucket: java-ai-tlias-002） |
