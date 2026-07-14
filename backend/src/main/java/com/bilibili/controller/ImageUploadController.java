package com.bilibili.controller;

import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.service.AliyunOssService;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/image")
public class ImageUploadController {

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB 后端兜底

    @Autowired
    private AliyunOssService aliyunOssService;

    @PostMapping("/upload")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        StpUtil.checkLogin();

        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ApiResponse.error(400, "不支持的图片格式，仅支持 jpg/png/webp");
        }

        if (file.getSize() > MAX_SIZE) {
            return ApiResponse.error(400, "图片过大，最大支持 5MB");
        }

        try {
            String userId = StpUtil.getLoginIdAsString();
            String url = aliyunOssService.uploadImage(file, userId);
            return ApiResponse.success(url);
        } catch (IOException e) {
            return ApiResponse.error(500, "上传失败：" + e.getMessage());
        }
    }
}
