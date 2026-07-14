## Context

Agnes AI Platform 当前是一个多模态 AI 应用，支持纯文本聊天、文生图、图生图、视频生成。聊天模块使用 Agnes 2.0 Flash 模型，通过 OpenAI 兼容的 SSE 接口调用。现有文件上传功能基于 Apache Tika 做文档文字提取，仅支持 txt/pdf/doc/xls 等文档类型，不支持图片内容的视觉理解。

Agnes 2.0 Flash 模型本身支持 Vision 能力（同一 messages 请求中传入 text + image_url），但当前后端消息构建逻辑只支持纯字符串 content，前端也没有图片上传入口。

项目技术栈：
- 后端：Spring Boot 3 + JPA + Redis + Sa-Token 认证
- 前端：Vue 3 + Vite + Pinia + Axios
- 部署：前端静态资源由 Spring Boot  serving

## Goals / Non-Goals

**Goals:**
- 用户可在聊天界面上传图片（按钮/拖拽/粘贴三种方式）
- 图片在前端自动压缩至 ≤1MB，上传到阿里云 OSS 获取公网 URL
- 后端将图片 URL 以 Vision 格式发送给 Agnes API
- 保持与现有纯文本聊天的完全向后兼容
- 对话历史正确保存和回显图片消息

**Non-Goals:**
- 不支持图片编辑/裁剪/滤镜
- 不支持从外部 URL 抓取图片（仅支持用户上传）
- 不支持图片批量下载/管理页面
- 不修改现有的文档文件（Tika）上传功能
- 不做图片内容审核/敏感检测（后续迭代）

## Decisions

### D1: 图片上传走后端接口而非前端直传 OSS

**决策**: 前端将图片以 multipart/form-data 发送到后端 `/api/image/upload`，后端负责上传 OSS 并返回 URL。

**理由**:
- 后端已有 Sa-Token 认证，可直接校验用户身份，避免前端直传 OSS 所需的签名计算和凭证暴露
- 后端可在上传前做压缩校验（尺寸、格式、大小）
- 与现有文件上传模式一致（`/api/chat/upload`），降低认知成本
- OSS 访问密钥只需存储在后端配置中

**替代方案**: 前端预签名直传 — 需要后端先调用 STS 获取临时凭证，增加复杂度且暴露更多网络往返。

### D2: 前端压缩 + 后端兜底

**决策**: 前端使用 Canvas API 在上传前压缩图片至 1MB 以内；后端也做大小校验，超限则拒绝。

**理由**:
- 前端压缩减少带宽消耗和上传时间
- 后端校验作为安全兜底，防止绕过前端的直接 API 调用

### D3: 使用 CNAME 域名作为图片公网 URL

**决策**: 图片上传后使用 CNAME 域名 `https://java-ai-tlias-002.cn-beijing.taihangpkx.cn/{objectId}` 作为发送给 Agnes API 的图片 URL。

**理由**:
- CNAME 域名已配置且支持外网访问，比原始 OSS Bucket 域名更稳定
- 不依赖 OSS 控制台临时签名 URL（有过期时间）
- 图片设为公共读即可永久访问

**替代方案**: 使用 Bucket 域名 — 同样可行，但 CNAME 更规范。

### D4: 消息体格式改造为 Jackson Polymorphic

**决策**: `AgnesChatRequest.Message` 的 content 字段从 `String` 改为 `Object`（Jackson 的 `JsonNode` 或 `List<Object>`），支持两种格式：
- 纯文本: `"content": "hello"`
- Vision: `"content": [{"type":"text","text":"描述"},{"type":"image_url","image_url":{"url":"..."}}]`

**理由**:
- Agnes API 的 Vision 请求使用数组格式，后端需要精确映射
- 使用 `Object` 类型 + Jackson 自动序列化，不需要手动拼 JSON
- 对纯文本消息完全透明（`"content": "text"` 仍可正常序列化）

**替代方案**: 固定使用数组格式，纯文本包装为 `[{"type":"text","text":"..."}]` — 但这样需要改动历史消息读取逻辑，风险更高。

### D5: ~~OSS 文件定时清理~~ → 暂不实现

**决策**: 本次迭代不做 OSS 图片定时清理。图片永久存储在 OSS 上（公共读），后续如有成本需求再补充清理逻辑。

**理由**:
- 本次迭代优先保证核心功能上线，清理逻辑后续按需补充
- 单张图片存储成本极低（~50KB），暂不构成压力

### D6: 图片上传日志记录

**决策**: 用户发送带图片的聊天消息时，在 `AgnesService.chatStreamReal()` 中调用 `LogService.log()`，创建 `IMAGE_UPLOAD` 类型的操作日志。`resultDetail` 写入图片公网 URL。上传失败时也记录日志，`resultStatus = "FAILED"`。

**理由**:
- 复用现有 `LogService` 异步写入机制，不阻塞主请求线程
- 管理员面板已有日志表格和 `resultDetail` 列展示，无需前端改动
- `IMAGE_UPLOAD` 独立类型方便管理员筛选

**替代方案**: 在 `ImageUploadController` 上传成功时记日志 — 但用户可能上传后不发送消息，产生无效日志。

## Risks / Trade-offs

| Risk | 影响 | 缓解措施 |
|------|------|----------|
| OSS 密钥泄露 | 攻击者滥用 OSS 资源 | 密钥存配置中心不入库；OSS Bucket 设置防盗链和每日流量上限 |
| 前端压缩失真 | 图片质量下降 | Canvas 压缩使用 0.8 质量因子，对常见截图/照片影响不大 |
| 大并发上传 | OSS 限流或后端内存溢出 | 限制单用户每分钟上传次数；使用流式上传不加载全量到内存 |
| Vision 格式不兼容 | Agnes API 拒绝请求 | 严格遵循 API 文档格式；纯文本消息保持原有 string 格式不变 |
| 图片 URL 失效 | 模型无法读取图片 | OSS 文件设为公共读 |

## Migration Plan

1. **开发环境配置**: 在 `application-dev.yml` 中添加 OSS 配置项
2. **后端开发**: 新增 `AliyunOssService` → 改造 `AgnesService` → 新增 `ImageUploadController`
3. **前端开发**: 新增图片上传组件逻辑 → 改造消息发送 → 预览/标签 UI
4. **联调测试**: 单图/压缩/格式校验/流式响应/历史回显
5. **部署**: OSS Bucket 设为公共读；更新 `application.yml` 生产配置

无破坏性变更 — 现有纯文本聊天功能完全不受影响。

## Open Questions

1. ~~是否需要限制单次对话中最多上传的图片数量？~~ → **已决定：每次仅限 1 张**
2. ~~OSS 清理策略~~ → **已决定：本次暂不实现，后续按需补充**
3. 是否需要为图片 URL 添加访问统计/审计日志？ → 暂不需要
