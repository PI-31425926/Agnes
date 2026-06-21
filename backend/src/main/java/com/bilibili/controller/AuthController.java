package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.LogService;
import com.bilibili.service.UserService;
import com.bilibili.utils.AesUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private AesUtil aesUtil;
    @Autowired
    private LogService logService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String apiKey = body.get("apiKey");
        // 校验...
        User user = new User();
        user.setPhone(phone);
        try {
            user.setApiKey(aesUtil.encrypt(apiKey)); // 加密存储
        } catch (Exception e) {
            return ResponseEntity.status(500).body("加密失败");
        }
        userRepository.save(user);
        return ResponseEntity.ok("注册成功");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,HttpServletRequest request) {
        String phone = body.get("phone");
        String apiKey = body.get("apiKey");
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body("手机号未注册");
        }
        try {
            String decryptedApiKey = aesUtil.decrypt(user.getApiKey());
            if (!decryptedApiKey.equals(apiKey)) {
                return ResponseEntity.status(401).body("API密钥错误");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("解密失败");
        }
        // Sa-Token 登录
        StpUtil.login(phone);

        String token = StpUtil.getTokenValue();
        // 记录登录日志，显式传入手机号和IP
        String ip = getClientIp(request);
        RequestContext.setCurrentUser(phone);  // 临时设置，方便日志记录
        logService.logLogin("LOGIN", "用户登录", "SUCCESS", phone, ip);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("role", user.getRole());
        return ResponseEntity.ok(result);
        /*RequestContext.clear();                 // 登录完成后清除
        return ResponseEntity.ok(Map.of("token", token));*/
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        StpUtil.logout();
        return ResponseEntity.ok("已退出");
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser() {
        String phone = StpUtil.getLoginIdAsString();
        User user = userService.findByPhone(phone);
        if (user == null) {
            return ResponseEntity.status(401).body("用户不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("phone", phone);
        map.put("role", user.getRole());
        return ResponseEntity.ok(map);
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
