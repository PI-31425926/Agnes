## Context

当前后端有两条并行响应约定：`ApiResponse<T>` 封装（VideoController）和裸 DTO/Map/String（其他 Controller）。前端 `unwrapResponse()` 用 hack 兼容，但脆弱且不一致。

## Goals / Non-Goals

**Goals:**
- 所有 REST 端点统一返回 `ApiResponse<T>` 格式
- 通过 `ResponseBodyAdvice` 自动包装，零侵入现有代码
- 前端 Axios 拦截器自动解包，移除 `unwrapResponse`
- 保持向后兼容：已返回 `ApiResponse` 的接口不受影响

**Non-Goals:**
- 不改动 SSE 流式接口（chat/stream）
- 不改动 WebSocket 端点
- 不引入新依赖

## Decisions

### Decision 1: ResponseBodyAdvice 自动包装
**选择**: 创建 `@RestControllerAdvice` + `ResponseBodyAdvice`，拦截所有 controller 返回值，如果不是 `ApiResponse` 则自动包装。
**理由**: 零侵入——不需要修改每个 controller 方法的返回类型，已有的 `ApiResponse` 返回也正确跳过。
**备选**: 手动修改每个 controller 方法 — 已否决，改动量大且易遗漏。

### Decision 2: Axios 拦截器解包
**选择**: 在 `api/request.js` 的 response interceptor 中 `return response.data.data`。
**理由**: 拦截器在所有 API 调用之前执行，一处修改全局生效。
**备选**: 手动在每个调用处解包 — 已否决，违背 DRY 原则。

### Decision 3: 错误处理保留 HTTP 状态码
**选择**: 成功返回 HTTP 200 + `ApiResponse.success(data)`；业务错误返回 HTTP 200 + `ApiResponse.error(code, msg)`。
**理由**: 与现有风格一致，前端通过 code 字段判断成功/失败。
**备选**: 业务错误返回 HTTP 4xx/5xx — 已否决，需要改动所有 controller 的异常处理。

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| 前端 `unwrapResponse` 被移除后，某些调用可能遗漏 | 全面搜索替换，逐个验证 |
| `ApiResponse` 的 `data` 为 null 时前端解包可能出错 | Axios 拦截器检查 `response.data` 是否存在 |
| 第三方前端（如 admin.js）可能未同步更新 | 一次性全部替换，不留遗留 |

## Migration Plan

1. 后端：新增 `ResponseBodyAdvice` → 修改裸返回 controller → 前端：Axios 拦截器 → 移除 `unwrapResponse`
2. 零停机：后端新增 `ResponseBodyAdvice` 后可先部署后端，前端后续部署
3. 回滚：移除 `ResponseBodyAdvice` 和拦截器即可

## Open Questions

无。
