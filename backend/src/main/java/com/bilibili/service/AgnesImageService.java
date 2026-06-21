package com.bilibili.service;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesImageRequest;
import com.bilibili.pojo.dto.AgnesImageResponse;
import com.bilibili.pojo.dto.AgnesImageToImageRequest;
import com.bilibili.pojo.dto.ImageHistoryItem;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgnesImageService {
    @Value("${agnes.image-api-url}")
    private String imageApiUrl;

    @Value("${agnes.image-model}")
    private String imageModel;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private LogService logService;

    @Autowired
    private RedisTemplate redisTemplate;

    private final RestTemplate restTemplate;

    public AgnesImageService() {
        this.restTemplate = new RestTemplate();
    }

    public String generateImage(String prompt, String size) throws Exception {
        String url = null;
        String status = "SUCCESS";
        String userId = StpUtil.getLoginIdAsString();   // 确保已登录
        try {
            // 1. 请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = getCurrentUserApiKey();
            headers.setBearerAuth(apiKey);

            // 2. 请求体
            AgnesImageRequest body = new AgnesImageRequest();
            body.setModel(imageModel);
            body.setPrompt(prompt);
            body.setSize(size);

            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("response_format", "url");
            body.setExtraBody(extraBody);

            HttpEntity<AgnesImageRequest> entity = new HttpEntity<>(body, headers);

            // 3. 发送请求
            ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                    imageApiUrl, entity, AgnesImageResponse.class
            );

            // 4. 提取第一张图片的 URL
            AgnesImageResponse respBody = response.getBody();
            if (respBody != null && respBody.getData() != null && !respBody.getData().isEmpty()) {
                url = respBody.getData().get(0).getUrl();
                // 记录到 Redis
                addToImageHistory("text2img", prompt, url);
            } else {
                throw new Exception("Image generation failed: no data returned");
            }
        } catch (Exception e) {
            status = "FAILED";
            url = e.getMessage();
            throw e;
        } finally {
            logService.log("IMAGE_GENERATION", "文生图", prompt, status, url,userId);
        }
        return url;
    }

    public String generateImageFromImage(String prompt, String size, String imageBase64) throws Exception {
        String url = null;
        String status = "SUCCESS";
        String userId = StpUtil.getLoginIdAsString();   // 确保已登录
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = getCurrentUserApiKey();
            headers.setBearerAuth(apiKey);

            AgnesImageToImageRequest body = new AgnesImageToImageRequest();
            body.setModel(imageModel);
            body.setPrompt(prompt);
            body.setSize(size);

            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("response_format", "url");
            extraBody.put("image", Collections.singletonList(imageBase64));
            body.setExtraBody(extraBody);

            HttpEntity<AgnesImageToImageRequest> entity = new HttpEntity<>(body, headers);

            ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                    imageApiUrl, entity, AgnesImageResponse.class
            );

            AgnesImageResponse respBody = response.getBody();
            if (respBody != null && respBody.getData() != null && !respBody.getData().isEmpty()) {
                url = respBody.getData().get(0).getUrl();
                // 记录到 Redis
                addToImageHistory("img2img", prompt, url);
            } else {
                throw new Exception("Image-to-image generation failed: no data returned");
            }
        } catch (Exception e) {
            status = "FAILED";
            url = e.getMessage();
            throw e;
        } finally {
            logService.log("IMAGE_TO_IMAGE", "图生图", prompt, status, url,userId);
        }
        return url;
    }

    private static final String IMAGE_HISTORY_KEY_PREFIX = "image:history:";
    private static final int MAX_HISTORY_ITEMS = 10;
    private static final long IMAGE_HISTORY_TTL_MINUTES = 60;

    private void addToImageHistory(String type, String prompt, String url) {
        String userId = getCurrentUserId();
        String key = IMAGE_HISTORY_KEY_PREFIX + userId;
        ImageHistoryItem item = new ImageHistoryItem(type, prompt, url, System.currentTimeMillis());
        redisTemplate.opsForList().leftPush(key, item);
        // 修剪长度
        redisTemplate.opsForList().trim(key, 0, MAX_HISTORY_ITEMS - 1);
        // 设置过期时间
        redisTemplate.expire(key, Duration.ofMinutes(IMAGE_HISTORY_TTL_MINUTES));
    }

    // 获取当前用户的图片历史列表
    public List<ImageHistoryItem> getImageHistory() {
        String userId = getCurrentUserId();
        String key = IMAGE_HISTORY_KEY_PREFIX + userId;
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null) return new ArrayList<>();
        return raw.stream()
                .map(obj -> (ImageHistoryItem) obj)
                .collect(Collectors.toList());
    }

    // 从 SecurityContext 获取当前用户手机号（图片生成在主线程，可用）
    private String getCurrentUserId() {
        // 1. 从 Sa-Token 获取当前登录的手机号
        return StpUtil.getLoginIdAsString();
    }

    private String getCurrentUserApiKey() {
        String phone = StpUtil.getLoginIdAsString();
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        try {
            return aesUtil.decrypt(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥");
        }
    }
}
