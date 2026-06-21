package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.ChatMessage;
import com.bilibili.pojo.dto.ChatRequest;
import com.bilibili.pojo.dto.ChatResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesService;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgnesService agnesService;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private UserRepository userRepository;

    public ChatController(AgnesService agnesService) {
        this.agnesService = agnesService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = agnesService.chat(request.getMessage());
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        return response;
    }

    // 流式对话（传递用户信息）
    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        // Sa-Token 校验登录
        StpUtil.checkLogin();  // 未登录会自动抛出 NotLoginException

        // 获取当前登录手机号
        String userId = StpUtil.getLoginIdAsString();

        // 根据手机号查询用户，并解密 API 密钥
        User user = userRepository.findByPhone(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String apiKey;
        try {
            apiKey = aesUtil.decrypt(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥", e);
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        agnesService.chatStreamReal(request.getMessage(), emitter, userId, apiKey);
        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory() {
        String userId = RequestContext.getCurrentUser();
        String historyKey = "chat:history:" + userId;
        List<ChatMessage> history = agnesService.getHistory(historyKey);
        return ResponseEntity.ok(history);
    }
}
