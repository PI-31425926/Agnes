package com.bilibili.controller;

import com.bilibili.common.context.RequestContext;
import com.bilibili.pojo.dto.ChatMessage;
import com.bilibili.pojo.dto.ChatRequest;
import com.bilibili.pojo.dto.ChatResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesService;
import com.bilibili.utils.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgnesService agnesService;

    @Autowired
    private AesUtil aesUtil;

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
        // 从 SecurityContext 获取当前用户信息（此时还在主线程）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("未登录");
        }
        String userId = auth.getName();
        String apiKey = null;
        if (auth.getPrincipal() instanceof User user) {
            try {
                apiKey = aesUtil.decrypt(user.getApiKey());
            } catch (Exception e) {
                throw new RuntimeException("无法解密API密钥", e);
            }
        } else {
            throw new RuntimeException("无法获取API密钥");
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
