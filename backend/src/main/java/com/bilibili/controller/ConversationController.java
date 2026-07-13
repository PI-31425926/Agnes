package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.mapper.ConversationRepository;
import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.dto.ConversationDTO;
import com.bilibili.pojo.entity.Conversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping
    public ApiResponse<List<ConversationDTO>> listConversations() {
        String phone = StpUtil.getLoginIdAsString();
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByCreatedAtDesc(Long.parseLong(phone));
        List<ConversationDTO> dtos = conversations.stream().map(c -> {
            System.out.println("[Conversation] List: id=" + c.getId() + ", type=" + (c.getId() != null ? c.getId().getClass().getName() : "null") + ", title=" + c.getTitle());
            return new ConversationDTO(c.getId(), c.getTitle(), c.getCreatedAt());
        }).collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @PostMapping
    public ApiResponse<ConversationDTO> createConversation(@RequestBody(required = false) String title) {
        String phone = StpUtil.getLoginIdAsString();
        Conversation conv = new Conversation();
        conv.setUserId(Long.parseLong(phone));
        conv.setTitle(title != null && !title.isBlank() ? title : "");
        conv = conversationRepository.save(conv);
        System.out.println("[Conversation] Created: id=" + conv.getId() + ", type=" + (conv.getId() != null ? conv.getId().getClass().getName() : "null") + ", title=" + conv.getTitle());
        ConversationDTO dto = new ConversationDTO(conv.getId(), conv.getTitle(), conv.getCreatedAt());
        System.out.println("[Conversation] DTO: id=" + dto.getId() + ", type=" + (dto.getId() != null ? dto.getId().getClass().getName() : "null"));
        return ApiResponse.success(dto);
    }

    @PutMapping("/{id}/title")
    public ApiResponse<String> updateTitle(@PathVariable Long id, @RequestBody String title) {
        String phone = StpUtil.getLoginIdAsString();
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("对话不存在"));
        if (!conv.getUserId().toString().equals(phone)) {
            return ApiResponse.error(403, "无权修改");
        }
        conv.setTitle(title);
        conversationRepository.save(conv);
        return ApiResponse.success("标题已更新");
    }

    @PutMapping("/{id}/auto-title")
    public ApiResponse<String> autoUpdateTitle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String firstMessage = body != null ? body.get("title") : "";
        if (firstMessage == null) firstMessage = "";
        String phone = StpUtil.getLoginIdAsString();
        Conversation conv = conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("对话不存在"));
        if (!conv.getUserId().toString().equals(phone)) {
            return ApiResponse.error(403, "无权修改");
        }
        String title = firstMessage.length() > 20 ? firstMessage.substring(0, 20) + "..." : firstMessage;
        conv.setTitle(title);
        conversationRepository.save(conv);
        return ApiResponse.success("标题已更新");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteConversation(@PathVariable Long id) {
        String phone = StpUtil.getLoginIdAsString();
        // 先清理 Redis 中的对话历史
        Conversation conv = conversationRepository.findById(id).orElse(null);
        if (conv != null) {
            String historyKey = "chat:history:" + phone + ":" + id;
            redisTemplate.delete(historyKey);
        }
        conversationRepository.deleteByUserIdAndId(Long.parseLong(phone), id);
        return ApiResponse.success("删除成功");
    }
}
