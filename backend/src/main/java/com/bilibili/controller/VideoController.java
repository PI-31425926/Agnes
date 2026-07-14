package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.*;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesVideoService;
import com.bilibili.service.AliyunOssService;
import com.bilibili.utils.AesUtil;
import com.bilibili.utils.VideoTaskManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final AgnesVideoService agnesVideoService;
    private final VideoTaskManager taskManager;  // 注入管理器

    @Autowired
    private AliyunOssService aliyunOssService;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private UserRepository userRepository;

    public VideoController(AgnesVideoService agnesVideoService, VideoTaskManager taskManager) {
        this.agnesVideoService = agnesVideoService;
        this.taskManager = taskManager;
    }

    // 上传单张图片到 OSS
    @PostMapping("/upload-image")
    public ApiResponse<String> uploadSingleImage(@RequestParam("file") MultipartFile file) {
        StpUtil.checkLogin();
        validateVideoImage(file);

        try {
            String userId = StpUtil.getLoginIdAsString();
            String url = aliyunOssService.uploadImage(file, userId);
            return ApiResponse.success(url);
        } catch (IOException e) {
            return ApiResponse.error(500, "上传失败：" + e.getMessage());
        }
    }

    // 上传多张图片到 OSS（关键帧用）
    @PostMapping("/upload-images")
    public ApiResponse<List<String>> uploadMultipleImages(@RequestParam("files") MultipartFile[] files) {
        StpUtil.checkLogin();

        if (files.length == 0 || files.length > 10) {
            return ApiResponse.error(400, "关键帧图片数量必须在 1-10 张之间");
        }

        List<String> urls = new ArrayList<>();
        String userId = StpUtil.getLoginIdAsString();
        for (MultipartFile file : files) {
            validateVideoImage(file);
            try {
                String url = aliyunOssService.uploadImage(file, userId);
                urls.add(url);
            } catch (IOException e) {
                throw new RuntimeException("上传失败：" + e.getMessage());
            }
        }
        return ApiResponse.success(urls);
    }

    private void validateVideoImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("文件为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !List.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new RuntimeException("不支持的图片格式，仅支持 jpg/png/webp");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("图片过大，最大支持 5MB");
        }
    }

    // 创建视频任务（支持多种模式）
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<?>> generateVideo(@RequestBody VideoGenerationRequest request) {
        // 参数校验
        String validationError = validateVideoParams(request);
        if (validationError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, validationError));
        }

        // 获取当前用户 API 密钥原文
        String userId = getCurrentUserId();
        String apiKey = getCurrentUserApiKeyPlain();
        try {
            AgnesVideoCreateResponse createResp;

            // 根据模式路由到不同的 Service 方法
            String mode = request.getMode() != null ? request.getMode() : "ti2vid";
            if ("i2vid".equals(mode)) {
                // 图生视频：取第一张图片 URL
                String imageUrl = (request.getImageUrls() != null && !request.getImageUrls().isEmpty())
                        ? request.getImageUrls().get(0)
                        : request.getImageUrl();
                if (imageUrl == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error(400, "图生视频需要提供图片 URL"));
                }
                createResp = agnesVideoService.createVideoTaskWithImage(
                        request.getPrompt(), imageUrl,
                        request.getWidth(), request.getHeight(),
                        request.getNumFrames(), request.getFrameRate(),
                        apiKey);
            } else if ("keyframes".equals(mode)) {
                // 关键帧动画
                if (request.getImageUrls() == null || request.getImageUrls().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error(400, "关键帧模式需要提供图片 URL"));
                }
                Map<String, Object> extraBody = new HashMap<>();
                extraBody.put("image", request.getImageUrls());
                extraBody.put("mode", "keyframes");
                createResp = agnesVideoService.createVideoTaskWithKeyframes(
                        request.getPrompt(), extraBody,
                        request.getWidth(), request.getHeight(),
                        request.getNumFrames(), request.getFrameRate(),
                        apiKey);
            } else {
                // 文生视频（默认）
                createResp = agnesVideoService.createVideoTask(
                        request.getPrompt(),
                        request.getWidth(), request.getHeight(),
                        request.getNumFrames(), request.getFrameRate(),
                        apiKey);
            }

            VideoTaskInfo taskInfo = new VideoTaskInfo();
            taskInfo.setTaskId(createResp.getTaskId());
            taskInfo.setVideoId(createResp.getVideoId());
            taskInfo.setStatus(createResp.getStatus());
            taskInfo.setProgress(createResp.getProgress());
            taskInfo.setPrompt(request.getPrompt());
            taskInfo.setCreatedAt(System.currentTimeMillis());
            taskInfo.setUserId(userId);

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

    /**
     * 视频参数校验
     */
    private String validateVideoParams(VideoGenerationRequest request) {
        // num_frames 校验
        int numFrames = request.getNumFrames();
        if (numFrames > 441) {
            return "帧数不能超过 441";
        }
        if (numFrames < 1) {
            return "帧数必须大于 0";
        }
        // 8n + 1 规则校验
        if ((numFrames - 1) % 8 != 0) {
            return String.format("帧数必须符合 8n+1 规则（如 81, 121, 241, 409），当前值: %d", numFrames);
        }
        // frame_rate 校验
        int frameRate = request.getFrameRate();
        if (frameRate < 1 || frameRate > 60) {
            return "帧率必须在 1-60 之间";
        }
        return null;
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
            return aesUtil.decryptLegacy(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密用户API密钥", e);
        }
    }

    // 获取所有任务（前端展示用）
    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<?>> getTasks() {
        String userId = getCurrentUserId();
        List<VideoTaskInfo> tasks = taskManager.getTasksByUser(userId);
        //List<VideoTaskInfo> tasks = taskManager.getAllTasks();
        // 可以按创建时间倒序
        tasks.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    // 删除任务（需校验归属）
    @DeleteMapping("/tasks/{videoId}")
    public ResponseEntity<ApiResponse<?>> deleteTask(@PathVariable String videoId) {
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
     * @param <?> 泛型类型，根据调用方决定
     * @return 包装的错误响应
     */
    private ResponseEntity<ApiResponse<?>> handleAgnesError(HttpStatusCodeException e) {
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


