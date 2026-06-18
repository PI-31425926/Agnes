package com.bilibili.controller;

import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.LogService;
import com.bilibili.utils.AesUtil;
import com.bilibili.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuthenticationManager authenticationManager;
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
        String token = jwtUtil.generateToken(user); // user 实现了 UserDetails
        // 记录登录日志（带 IP）
        // 记录登录日志，显式传入手机号和IP
        String ip = getClientIp(request);
        RequestContext.setCurrentUser(phone);  // 临时设置，方便日志记录
        logService.logLogin("LOGIN", "用户登录", "SUCCESS", phone, ip);
        RequestContext.clear();                 // 登录完成后清除
        /*String ip = getClientIp(request);
        logService.logWithIp("LOGIN", "用户登录", null, "SUCCESS", null, ip);*/

        return ResponseEntity.ok(Map.of("token", token));
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
