## 1. 后端 — 依赖与配置

- [x] 1.1 添加 aliyun-sdk-oss Maven 依赖到 pom.xml
- [x] 1.2 在 application.yml / application-dev.yml 中添加 OSS 配置段（endpoint, bucket, accessKeyId, accessKeySecret, cnameUrl）
- [x] 1.3 创建 `OssProperties` 配置类，使用 `@ConfigurationProperties(prefix = "oss")` 绑定配置

## 2. 后端 — AliyunOssService

- [x] 2.1 创建 `AliyunOssService`，封装 OSSClient 初始化和图片上传方法
- [x] 2.2 实现 `uploadImage(MultipartFile file)` → 返回图片公网 URL（使用 CNAME 域名格式）
- [x] 2.3 上传时生成唯一 objectName（格式：`chat-images/{userId}/{timestamp}-{uuid}.jpg`）
- [x] 2.4 上传失败抛出明确异常，Controller 层捕获返回 500

## 3. 后端 — ImageUploadController

- [x] 3.1 创建 `ImageUploadController`，定义 `POST /api/image/upload` 接口
- [x] 3.2 接收 multipart/form-data 参数 `file`，校验格式（jpg/png/webp）和大小（≤5MB 后端兜底）
- [x] 3.3 调用 `AliyunOssService.uploadImage()` 上传，返回 `ApiResponse<String>` 包含公网 URL

## 4. 后端 — AgnesService Vision 改造

- [x] 4.1 修改 `AgnesChatRequest.Message` 的 content 字段从 `String` 改为 `Object`（Jackson `JsonNode` 或 `List<Object>`）
- [x] 4.2 改造 `chatStreamReal()` 方法：当 `ChatRequest.imageUrl` 非空时，构建 Vision 格式的 content 数组
- [x] 4.3 纯文本消息保持原有 string content 格式不变（向后兼容）
- [x] 4.4 带图片的消息构造：`[{"type":"text","text":"用户消息"},{"type":"image_url","image_url":{"url":"oss-public-url"}}]`

## 5. 后端 — ChatRequest DTO 与 ChatController 改造

- [x] 5.1 在 `ChatRequest` 中新增 `String imageUrl` 字段（单张图片 URL）
- [x] 5.2 在 `ChatController.chatStream()` 中将 imageUrl 透传给 `AgnesService`
- [x] 5.3 确保 SSE 流式响应的解析和转发逻辑不受影响

## 6. 后端 — 图片上传日志

- [x] 6.1 在 `AgnesService.chatStreamReal()` 中，带图片消息发送完成后调用 `LogService.log("IMAGE_UPLOAD", "图片识别: <描述>", null, "SUCCESS", imageUrl)`
- [x] 6.2 发送失败时也记录日志：`LogService.log("IMAGE_UPLOAD", "图片识别失败", null, "FAILED", imageUrl + " | " + errorMsg)`
- [x] 6.3 确保日志写入为异步（复用 `@Async`），不阻塞 SSE 流式响应
- [x] 6.4 管理员面板日志表格中 `resultDetail` 列可正常显示图片 URL（hover 查看全文）

## 7. 前端 — 图片选择与预览组件

- [x] 6.1 在 `MainView.vue` 聊天输入区域上方添加图片上传按钮（📷 图标）
- [x] 6.2 创建 `selectedImage` 响应式变量（单对象，非数组），存储 `{file, previewUrl, objectName, url}`
- [x] 6.3 实现三种上传方式：
  - 点击按钮触发 `<input type="file" accept="image/jpeg,image/png,image/webp">`
  - 拖拽图片到输入区域（监听 `dragover`/`drop` 事件）
  - 粘贴图片（监听 `paste` 事件，从 `clipboard.items` 读取 image 数据）
- [x] 6.4 显示图片预览缩略图和文件名标签
- [x] 6.5 点击关闭按钮移除已选图片
- [x] 6.6 已选择图片后，再次选择新图片时提示用户先移除当前图片

## 8. 前端 — 图片压缩

- [x] 7.1 创建 `compressImage(file, maxSizeBytes)` 函数，使用 Canvas API
- [x] 7.2 压缩策略：先将图片 resize 到最大边长 1920px，再以 0.8 质量 factor 调用 `canvas.toBlob(callback, 'image/jpeg')`
- [x] 7.3 递归压缩：如果压缩后仍 >1MB，降低质量因子重试，直到 ≤1MB 或质量达到最低阈值
- [x] 7.4 压缩后的 Blob 转 FormData 用于上传

## 9. 前端 — 图片上传 API

- [x] 8.1 在 `api/chat.js` 中新增 `uploadImage(file: File): Promise<string>` 方法
- [x] 8.2 使用 `FormData` 发送 `POST /api/image/upload`，`Content-Type: multipart/form-data`
- [x] 8.3 解析返回的 `ApiResponse.data` 为图片公网 URL
- [x] 8.4 上传失败时显示错误 toast 提示

## 10. 前端 — 消息发送改造

- [x] 9.1 改造 `sendChat()` / `sendChatStream()` 方法，在发送 JSON body 时附加 `imageUrl: selectedImage?.url`
- [x] 9.2 发送成功后清空 `selectedImage` 和预览 UI
- [x] 9.3 前端消息列表中，带图片的消息显示为卡片形式（图片缩略图 + 文字）
- [x] 9.4 图片点击可放大预览

## 11. 前端 — 历史对话回显

- [x] 10.1 从 Redis 读取的历史消息中，识别包含 `image_url` 类型的 content 结构
- [x] 10.2 在历史对话列表中，带图片的消息渲染为图片卡片
- [x] 10.3 确保历史消息的回显不影响纯文本消息的渲染

## 12. 联调与测试

- [x] 12.1 测试纯文本消息发送 — 确认行为与改动前一致
- [x] 12.2 测试单张图片上传 + 发送 — 确认模型正确返回图片识别结果
- [x] 12.3 测试管理员面板日志中 `IMAGE_UPLOAD` 类型记录可见且 URL 可 hover 查看
- [x] 12.4 测试大图压缩（5MB 图片 → 压缩至 ≤1MB）
- [x] 12.5 测试不支持的格式（gif/bmp/svg）拒绝上传
- [x] 12.6 测试拖拽和粘贴上传
- [x] 12.7 测试 SSE 流式响应正常输出
- [x] 12.8 测试历史对话中图片消息的回显
