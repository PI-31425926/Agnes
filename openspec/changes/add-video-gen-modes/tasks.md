## 1. 后端 DTO 扩展

- [x] 1.1 扩展 AgnesVideoCreateRequest.java，新增 image（单图 URL）、mode（生成模式）、extraBody（Object 类型，承载关键帧参数）字段及对应 getter/setter
- [x] 1.2 扩展 VideoGenerationRequest.java，新增 mode（String）、imageUrls（List\<String>）字段，保留原有 prompt/width/height/numFrames/frameRate

## 2. 后端 Service 层

- [x] 2.1 在 AgnesVideoService.java 中新增 buildVideoRequestBody() 私有方法，根据 mode 和 imageUrls 组装不同的请求体（文生视频用 prompt  alone，图生视频加 image 字段，关键帧加 extra_body）
- [x] 2.2 在 AgnesVideoService.java 中新增 createVideoTaskWithImage() 方法，接收 prompt + imageUrl + 参数，调用 buildVideoRequestBody 并发送 API 请求
- [x] 2.3 在 AgnesVideoService.java 中新增 createVideoTaskWithKeyframes() 方法，接收 prompt + imageUrls + 参数，调用 buildVideoRequestBody 并发送 API 请求
- [x] 2.4 更新日志记录描述：根据模式区分记录 "文生视频/图生视频/关键帧动画"

## 3. 后端 Controller 层

- [x] 3.1 在 VideoController.java 中新增 `/api/video/upload-image` 端点，接收单文件上传，调用 AliyunOssService 上传并返回 URL
- [x] 3.2 在 VideoController.java 中新增 `/api/video/upload-images` 端点，接收多文件上传，返回 URL 数组
- [x] 3.3 重构 VideoController.generateVideo() 方法，根据 request.getMode() 路由到不同的 Service 方法（文生视频→原方法，图生视频→createVideoTaskWithImage，关键帧→createVideoTaskWithKeyframes）
- [x] 3.4 在后端添加参数校验：num_frames ≤ 441 且符合 8n+1 规则，frame_rate 范围 1-60，关键帧图片数量 ≤ 10

## 4. 前端视频面板 UI

- [x] 4.1 在 MainView.vue 视频面板的新建任务折叠区内新增模式选择器（下拉框：文生视频/图生视频/关键帧动画），默认选中"文生视频"
- [x] 4.2 新增视频专用图片上传组件：选择"图生视频"时显示单图上传区，选择"关键帧动画"时显示多图上传区
- [x] 4.3 实现图片上传逻辑：调用 `/api/video/upload-image` 或 `/api/video/upload-images`，上传后显示缩略图预览
- [x] 4.4 实现图片删除功能：点击 x 按钮移除已上传的图片，多图模式下支持逐个删除
- [x] 4.5 实现 submitVideoTask() 逻辑更新：根据选择的模式构建不同的请求体，包含 mode 和 imageUrls

## 5. 前端样式

- [x] 5.1 为模式选择器添加样式（与现有 param-group/options-row 风格一致）
- [x] 5.2 为视频图片上传区域添加样式（复用图生图的 upload-row/preview-box 样式模式）
- [x] 5.3 为视频图片缩略图添加样式（圆角边框、hover 效果）

## 6. 验证与联调

- [x] 6.1 测试文生视频功能不受影响（回归测试）
- [x] 6.2 测试图生视频完整流程：上传图片 → 选择模式 → 输入 prompt → 提交 → WebSocket 推送 → 查看结果
- [x] 6.3 测试关键帧动画完整流程：上传多张图 → 选择模式 → 输入 prompt → 提交 → WebSocket 推送 → 查看结果
- [x] 6.4 测试参数边界情况：num_frames 超出 441、不符合 8n+1 规则、帧率超出范围等
