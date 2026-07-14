## ADDED Requirements

### Requirement: 用户可上传图片文件
系统 SHALL 允许用户在聊天界面**每次上传一张**图片文件，支持 jpg、png、webp 格式，单张图片大小不超过 1MB。超过 1MB 的图片应在上传前自动压缩至 1MB 以内。已选择图片后不可再选，需先移除才能重新选择。

#### Scenario: 选择图片上传
- **WHEN** 用户点击聊天输入框旁的图片上传按钮并选择一张图片
- **THEN** 系统显示图片预览缩略图和文件名标签，并提供移除按钮

#### Scenario: 拖拽图片到聊天区域
- **WHEN** 用户将图片文件拖拽到聊天输入区域
- **THEN** 系统识别图片并显示预览标签

#### Scenario: 粘贴图片到聊天区域
- **WHEN** 用户在输入框中按下 Ctrl+V 粘贴剪贴板中的图片
- **THEN** 系统识别剪贴板图片并显示预览标签

#### Scenario: 图片超过 1MB 自动压缩
- **WHEN** 用户选择的图片大小超过 1MB
- **THEN** 系统在前端使用 Canvas 自动压缩图片至 1MB 以内后再上传

#### Scenario: 不支持的图片格式拒绝
- **WHEN** 用户尝试上传非 jpg/png/webp 格式的文件
- **THEN** 系统显示错误提示，不执行上传

#### Scenario: 移除已选图片
- **WHEN** 用户点击预览标签上的移除按钮
- **THEN** 图片预览和标签被移除，该图片不会随下一条消息发送

#### Scenario: 已选图片后不可重复选择
- **WHEN** 用户已选择了一张图片但未发送，再次尝试选择新图片
- **THEN** 系统拒绝新选择，提示"已有一张图片，请先移除当前图片"

### Requirement: 图片上传至阿里云 OSS
系统 SHALL 将用户上传的图片通过后端接口上传至阿里云 OSS，获取并返回公开可访问的公网 URL。

#### Scenario: 上传图片到 OSS
- **WHEN** 前端通过 multipart/form-data 调用 `/api/image/upload` 接口
- **THEN** 后端将图片上传到阿里云 OSS bucket `java-ai-tlias-002`，返回包含公网 URL 的 JSON 响应

#### Scenario: OSS 上传失败处理
- **WHEN** 阿里云 OSS 上传失败（网络异常、凭证错误等）
- **THEN** 后端返回 500 错误及友好提示信息，前端显示上传失败

### Requirement: OSS 配置安全存储
系统 SHALL 将阿里云 OSS 访问凭证存储在配置文件 `application.yml` 中，不硬编码在源码里。开发环境使用明文配置，生产环境建议通过环境变量覆盖。

## REMOVED Requirements

<!-- None -->
