package com.bilibili.controller;

import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesService;
import com.bilibili.service.UserService;
import com.bilibili.utils.AesUtil;
import com.bilibili.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/guest")
public class GuestController {

    @Autowired
    private UserService userService;
    @Autowired
    private AesUtil aesUtil;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private AgnesService agnesService;

    /**
     * 游客对话（已弃用）
     * 游客模式已改为仅浏览，此端点保留仅供第三方兼容，前端不再调用。
     */
    @Deprecated
    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String ip = IpUtils.getClientIp(request);
        String limitKey = "guest:limit:" + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            return ApiResponse.error(429, "游客模式每分钟只能使用一次");
        }

        List<User> users = userService.findAll();
        if (users.isEmpty()) {
            return ApiResponse.error(503, "系统没有可用用户");
        }
        User randomUser = users.get(ThreadLocalRandom.current().nextInt(users.size()));
        String apiKey;
        try {
            apiKey = aesUtil.decryptLegacy(randomUser.getApiKey());
        } catch (Exception e) {
            return ApiResponse.error(500, "API 密钥解密失败");
        }

        redisTemplate.opsForValue().set(limitKey, String.valueOf(System.currentTimeMillis()),
                Duration.ofMinutes(1));

        String message = body.get("message");
        try {
            String reply = agnesService.chatWithApiKey(message, apiKey, randomUser.getPhone());
            return ApiResponse.success(Map.of("reply", reply));
        } catch (Exception e) {
            return ApiResponse.error(500, "对话失败");
        }
    }

}
