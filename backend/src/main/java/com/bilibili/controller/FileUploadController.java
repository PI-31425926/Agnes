package com.bilibili.controller;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件为空");
        }
        // 大小限制
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return ResponseEntity.badRequest().body("文件过大，最大支持 10MB");
        }
        try {
            Tika tika = new Tika();
            String content = tika.parseToString(file.getInputStream());
            String userId = getCurrentUserId(); // 从 SecurityContext 获取
            String key = FILE_CONTENT_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, content, Duration.ofMinutes(30));
            return ResponseEntity.ok("上传成功");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("解析失败：" + e.getMessage());
        }
    }

    // 清除已上传文件
    @DeleteMapping("/upload")
    public ResponseEntity<String> clearUploadedFile() {
        String userId = getCurrentUserId();
        redisTemplate.delete(FILE_CONTENT_KEY_PREFIX + userId);
        return ResponseEntity.ok("已清除");
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        throw new RuntimeException("未登录");
    }
}

/*@RestController
@RequestMapping("/api/chat")
public class FileUploadController {

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("文件为空");
        }

        // 限制文件大小（如 10MB）
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return ResponseEntity.badRequest().body("文件过大，最大支持 10MB");
        }

        try {
            Tika tika = new Tika();
            // Tika 自动识别文件类型并提取文本（包括 Excel）
            String content = tika.parseToString(file.getInputStream());
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("文件解析失败：" + e.getMessage());
        }
    }
}*/