package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.pojo.dto.ApiResponse;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/chat")
public class FileUploadController {

    public static final String FILE_CONTENT_KEY_PREFIX = "file:content:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/upload")
    public ApiResponse<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空");
        }
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return ApiResponse.error(400, "文件过大，最大支持 10MB");
        }
        try {
            Tika tika = new Tika();
            String content = tika.parseToString(file.getInputStream());
            String userId = getCurrentUserId();
            String key = FILE_CONTENT_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, content, Duration.ofMinutes(30));
            return ApiResponse.success("上传成功");
        } catch (Exception e) {
            return ApiResponse.error(500, "解析失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/upload")
    public ApiResponse<String> clearUploadedFile() {
        String userId = getCurrentUserId();
        redisTemplate.delete(FILE_CONTENT_KEY_PREFIX + userId);
        return ApiResponse.success("已清除");
    }

    private String getCurrentUserId() {
        return StpUtil.getLoginIdAsString();
    }
}