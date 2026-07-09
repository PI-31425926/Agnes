package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.LogService;
import com.bilibili.service.UserService;
import com.bilibili.utils.AesUtil;
import com.bilibili.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ApiResponse<String> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String apiKey = body.get("apiKey");
        User user = new User();
        user.setPhone(phone);
        try {
            user.setApiKey(aesUtil.encrypt(apiKey));
        } catch (Exception e) {
            return ApiResponse.error(500, "加密失败");
        }
        userRepository.save(user);
        return ApiResponse.success("注册成功");
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String phone = body.get("phone");
        String apiKey = body.get("apiKey");
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            return ApiResponse.error(401, "手机号未注册");
        }
        try {
            AesUtil.DecryptResult result = aesUtil.decrypt(user.getApiKey());
            String decryptedApiKey = result.plaintext();
            if (!decryptedApiKey.equals(apiKey)) {
                return ApiResponse.error(401, "API密钥错误");
            }
            if (result.legacy()) {
                user.setApiKey(aesUtil.encrypt(apiKey));
                userRepository.save(user);
            }
        } catch (Exception e) {
            return ApiResponse.error(500, "解密失败");
        }
        StpUtil.login(phone);

        String token = StpUtil.getTokenValue();
        String ip = IpUtils.getClientIp(request);
        try {
            RequestContext.setCurrentUser(phone);
            logService.logLogin("LOGIN", "用户登录", "SUCCESS", phone, ip);
        } finally {
            RequestContext.clear();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("role", user.getRole());
        return ApiResponse.success(data);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        StpUtil.logout();
        return ApiResponse.success("已退出");
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        String phone = StpUtil.getLoginIdAsString();
        User user = userService.findByPhone(phone);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("role", user.getRole());
        return ApiResponse.success(data);
    }

}
