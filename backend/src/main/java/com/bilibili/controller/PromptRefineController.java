package com.bilibili.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesChatRequest;
import com.bilibili.pojo.dto.AgnesChatResponse;
import com.bilibili.pojo.dto.ApiResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptRefineController {

    @Value("${agnes.api-url}")
    private String apiUrl;

    @Value("${agnes.model}")
    private String model;

    @Autowired private RestTemplate restTemplate;
    @Autowired private UserRepository userRepo;
    @Autowired private AesUtil aesUtil;

    private static final Map<String, String> SYSTEM_PROMPTS = Map.of(
            "text_to_image",
            "你是一个专业的图像提示词优化师。用户会给你一段描述，你需要将其扩展为适合文生图的详细提示词。" +
            "遵循结构：[主体] + [场景/环境] + [风格] + [光照] + [构图] + [质量要求]。" +
            "输出简洁、高信息密度的中文提示词，不要有多余解释。",

            "image_to_image",
            "你是一个专业的图像编辑提示词优化师。用户会给你修改要求，你需要将其扩展为适合图生图的详细指令。" +
            "遵循结构：[改变要求] + [新风格/场景] + [需要添加或移除的元素] + [需要保留的元素]。" +
            "输出简洁的中文提示词，不要有多余解释。",

            "text_to_video",
            "你是一个专业的视频提示词优化师。用户会给你一段描述，你需要将其扩展为适合文生视频的提示词。" +
            "遵循结构：[主体] + [动作] + [场景] + [镜头运动] + [光线] + [风格]。" +
            "输出简洁、生动的中文提示词，不要有多余解释。",

            "image_to_video",
            "你是一个专业的视频提示词优化师。用户会给你一段描述，你需要将其扩展为适合图生视频的提示词。" +
            "遵循结构：[主体] + [动作] + [场景] + [镜头运动] + [光线] + [风格]。" +
            "输出简洁、生动的中文提示词，不要有多余解释。",

            "keyframe_animation",
            "你是一个专业的关键帧动画提示词优化师。用户会给你动画描述，你需要将其扩展为适合关键帧动画的提示词。" +
            "清晰描述关键帧之间的过渡关系，保持角色身份一致，镜头角度稳定，动作自然流畅。" +
            "输出简洁的中文提示词，不要有多余解释。",

            "default",
            "你是一个专业的提示词优化师。将用户的简短描述扩展为详细、生动、富有画面感的提示词。" +
            "只输出优化后的提示词，不要有多余解释。"
    );

    @PostMapping("/refine")
    public ApiResponse<Map<String, String>> refinePrompt(@RequestBody Map<String, String> body) {
        String nodeType = body.get("type");
        String prompt = body.get("prompt");

        if (prompt == null || prompt.isBlank()) {
            return ApiResponse.error(400, "Prompt is required");
        }

        String systemPrompt = SYSTEM_PROMPTS.getOrDefault(nodeType, SYSTEM_PROMPTS.get("default"));

        List<AgnesChatRequest.Message> messages = new ArrayList<>();
        messages.add(new AgnesChatRequest.Message("system", systemPrompt));
        messages.add(new AgnesChatRequest.Message("user", "请优化这段提示词：" + prompt));

        AgnesChatRequest req = new AgnesChatRequest();
        req.setModel(model);
        req.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String apiKey = getCurrentUserApiKey();
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<AgnesChatResponse> response = restTemplate.postForEntity(
                    apiUrl, new HttpEntity<>(req, headers), AgnesChatResponse.class);

            String refined = extractContent(response.getBody());
            if (refined == null || refined.isBlank()) {
                return ApiResponse.error(500, "Empty response from LLM");
            }

            return ApiResponse.success(Map.of("refined_prompt", refined));
        } catch (Exception e) {
            return ApiResponse.error(500, "Refine failed: " + e.getMessage());
        }
    }

    private String extractContent(AgnesChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) return null;
        Object content = response.getChoices().get(0).getMessage().getContent();
        return content != null ? content.toString() : null;
    }

    private String getCurrentUserApiKey() {
        String phone = StpUtil.getLoginIdAsString();
        User user = userRepo.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        try {
            return aesUtil.decryptLegacy(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥");
        }
    }
}
