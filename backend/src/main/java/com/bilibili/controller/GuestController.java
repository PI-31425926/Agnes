package com.bilibili.controller;

import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesService;
import com.bilibili.service.UserService;
import com.bilibili.utils.AesUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String ip = getClientIp(request);
        String limitKey = "guest:limit:" + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("游客模式每分钟只能使用一次");
        }

        List<User> users = userService.findAll();
        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("系统没有可用用户");
        }
        // 随机选择一个用户
        User randomUser = users.get(new Random().nextInt(users.size()));
        String apiKey;
        try {
            apiKey = aesUtil.decrypt(randomUser.getApiKey());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("API 密钥解密失败");
        }

        // 设置限流标记（1 分钟）
        redisTemplate.opsForValue().set(limitKey, String.valueOf(System.currentTimeMillis()),
                Duration.ofMinutes(1));

        // 调用对话（非流式示例，流式可类似传递）
        String message = body.get("message");
        try {
            // 需要 AgnesService 提供 chatWithApiKey 方法
            String reply = agnesService.chatWithApiKey(message, apiKey, randomUser.getPhone());
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("对话失败");
        }
    }

    // 获取客户端真实 IP（考虑代理）
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个 IP 时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
