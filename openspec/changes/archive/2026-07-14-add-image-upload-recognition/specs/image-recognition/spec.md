## ADDED Requirements

### Requirement: 聊天消息支持图片识别
系统 SHALL 在发送聊天消息时将图片公网 URL 以 OpenAI Vision 格式嵌入消息体，发送给 Agnes 2.0 Flash 模型进行多模态识别。

#### Scenario: 纯文本消息兼容发送
- **WHEN** 用户发送不带图片的纯文本消息
- **THEN** 系统使用现有的纯文本格式发送消息，行为与改动前完全一致

#### Scenario: 带图片的消息发送
- **WHEN** 用户发送附带一张图片的文本消息
- **THEN** 系统将文本和图片 URL 组合为 OpenAI Vision 格式的 content 数组发送给 Agnes API

#### Scenario: 图片 URL 格式正确
- **WHEN** 构造发送给 Agnes API 的请求体
- **THEN** 图片 URL 使用 `image_url.url` 字段，值为阿里云 OSS 公网 HTTPS URL（如 `https://java-ai-tlias-002.cn-beijing.taihangpkx.cn/xxx.jpg`）

#### Scenario: 流式响应正常工作
- **WHEN** 带图片的消息发送给 Agnes API
- **THEN** 后端正确解析 Agnes API 返回的 SSE 流式响应，通过 SseEmitter 转发给前端

### Requirement: 对话历史保留图片引用
系统 SHALL 在保存对话历史时记录用户消息中包含的图片 URL，以便在多轮对话上下文中保持图片上下文。

#### Scenario: 包含图片的用户消息存入历史
- **WHEN** 用户发送了包含图片的消息
- **THEN** 系统将该消息的完整 content 结构（文本 + 图片 URL）存入 Redis 历史列表

#### Scenario: 历史消息回显
- **WHEN** 用户查看历史对话记录
- **THEN** 系统中包含图片的消息在列表中显示为带图片缩略图的卡片

### Requirement: 图片上传操作记录日志
系统 SHALL 在用户发送带图片的聊天消息时，异步记录一条 `IMAGE_UPLOAD` 类型的操作日志，`resultDetail` 字段包含图片的公网 URL，供管理员在管理面板查看。

#### Scenario: 发送带图片消息时记录日志
- **WHEN** 用户发送了一条附带图片的聊天消息
- **THEN** 系统异步写入一条 `OperationLog`，`operationType = "IMAGE_UPLOAD"`，`resultDetail` 包含图片公网 URL（如 `https://java-ai-tlias-002.cn-beijing.taihangpkx.cn/chat-images/...jpg`）

#### Scenario: 管理员可查看图片上传日志
- **WHEN** 管理员在管理面板查看操作日志
- **THEN** `IMAGE_UPLOAD` 类型的日志在日志表格中显示，`resultDetail` 列展示图片公网 URL（hover 可看全文）

#### Scenario: 图片上传失败时也记录日志
- **WHEN** 图片上传 OSS 成功但后续发送聊天消息失败
- **THEN** 系统仍记录 `IMAGE_UPLOAD` 日志，`resultStatus = "FAILED"`，`resultDetail` 包含图片 URL 和失败原因

## REMOVED Requirements

<!-- None -->
