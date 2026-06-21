package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.*;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesVideoService;
import com.bilibili.utils.AesUtil;
import com.bilibili.utils.VideoTaskManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final AgnesVideoService agnesVideoService;
    private final VideoTaskManager taskManager;  // 注入管理器

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private UserRepository userRepository;

    public VideoController(AgnesVideoService agnesVideoService, VideoTaskManager taskManager) {
        this.agnesVideoService = agnesVideoService;
        this.taskManager = taskManager;
    }

    // 创建视频任务
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<VideoTaskResponse>> generateVideo(@RequestBody VideoGenerationRequest request) {
        // 获取当前用户 API 密钥原文
        String userId = getCurrentUserId();
        String apiKey = getCurrentUserApiKeyPlain();
        try {
            AgnesVideoCreateResponse createResp = agnesVideoService.createVideoTask(
                    request.getPrompt(),
                    request.getWidth(),
                    request.getHeight(),
                    request.getNumFrames(),
                    request.getFrameRate(),
                    apiKey
            );

            VideoTaskInfo taskInfo = new VideoTaskInfo();
            taskInfo.setTaskId(createResp.getTaskId());
            taskInfo.setVideoId(createResp.getVideoId());
            taskInfo.setStatus(createResp.getStatus());
            taskInfo.setProgress(createResp.getProgress());
            taskInfo.setPrompt(request.getPrompt());
            taskInfo.setCreatedAt(System.currentTimeMillis());
            taskInfo.setUserId(userId);           // 设置归属
            taskInfo.setApiKey(apiKey);  // 保存 API 密钥原文，供轮询使用

            // 加入队列
            taskManager.addTask(taskInfo);

            return ResponseEntity.ok(ApiResponse.success(taskInfo));
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return handleAgnesError(e);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("最多同时拥有")) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error(429, e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "内部错误：" + e.getMessage()));
        }
    }

    private String getCurrentUserApiKeyPlain() {
        // 1. 检查是否已登录
        if (!StpUtil.isLogin()) {
            throw new RuntimeException("未登录");
        }
        // 2. 获取当前登录用户的手机号
        String phone = StpUtil.getLoginIdAsString();
        // 3. 从数据库查询用户
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        // 4. 解密 API 密钥并返回
        try {
            return aesUtil.decrypt(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密用户API密钥", e);
        }
    }

    // 获取所有任务（前端展示用）
    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<VideoTaskResponse>> getTasks() {
        String userId = getCurrentUserId();
        List<VideoTaskInfo> tasks = taskManager.getTasksByUser(userId);
        //List<VideoTaskInfo> tasks = taskManager.getAllTasks();
        // 可以按创建时间倒序
        tasks.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    // 删除任务（需校验归属）
    @DeleteMapping("/tasks/{videoId}")
    public ResponseEntity<ApiResponse<VideoTaskResponse>> deleteTask(@PathVariable String videoId) {
        String userId = getCurrentUserId();
        boolean deleted = taskManager.removeTask(videoId, userId);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success("删除成功"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "任务不存在或无权删除"));
    }

    // 提取当前用户手机号
    private String getCurrentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 统一处理 Agnes 返回的 HTTP 错误（如 503、429 等）
     * @param e 异常
     * @param <T> 泛型类型，根据调用方决定
     * @return 包装的错误响应
     */
    private <T> ResponseEntity<ApiResponse<T>> handleAgnesError(HttpStatusCodeException e) {
        String responseBody = e.getResponseBodyAsString();
        String message = "上游服务错误";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);
            JsonNode errorNode = root.path("error").path("message");
            if (!errorNode.isMissingNode()) {
                message = errorNode.asText();
            }
        } catch (Exception ignored) {}

        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.error(e.getStatusCode().value(), message));
    }
}


