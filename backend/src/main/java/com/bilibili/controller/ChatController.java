package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.dto.ChatMessage;
import com.bilibili.pojo.dto.ChatRequest;
import com.bilibili.pojo.dto.ChatResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.service.AgnesService;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ApiResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        String reply = agnesService.chat(request.getMessage());
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        return ApiResponse.success(response);
    }

    // 流式对话（传递用户信息）
    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        System.out.println("[ChatController] Received: message='" + request.getMessage() + "', conversationId='" + request.getConversationId() + "'");
        // Sa-Token 校验登录
        StpUtil.checkLogin();  // 未登录会自动抛出 NotLoginException

        // 获取当前登录手机号
        String userId = StpUtil.getLoginIdAsString();

        // 根据手机号查询用户，并解密 API 密钥
        User user = userRepository.findByPhone(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String apiKey;
        try {
            apiKey = aesUtil.decryptLegacy(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥", e);
        }

        String conversationId = request.getConversationId();
        System.out.println("[Chat] conversationId=" + conversationId);
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(e -> System.err.println("[SSE] 错误: " + e.getMessage()));
        agnesService.chatStreamReal(request.getMessage(), emitter, userId, apiKey, conversationId);
        return emitter;
    }

    @GetMapping("/history")
    public ApiResponse<List<ChatMessage>> getHistory(@RequestParam(required = false) String conversationId) {
        String userId = RequestContext.getCurrentUser();
        List<ChatMessage> history = agnesService.getHistoryByConversation(userId, conversationId);
        return ApiResponse.success(history);
    }
}
