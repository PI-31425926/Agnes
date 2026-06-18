package com.bilibili.service;

import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesChatRequest;
import com.bilibili.pojo.dto.AgnesChatResponse;
import com.bilibili.pojo.dto.ChatMessage;
import com.bilibili.pojo.dto.ChatResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgnesService {

    /*@Value("${agnes.api-key}")
    private String apiKey;*/

    @Value("${agnes.api-url}")
    private String apiUrl;

    @Value("${agnes.model}")
    private String model;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private LogService logService;

    private final RestTemplate restTemplate;

    public AgnesService() {
        this.restTemplate = new RestTemplate();
    }

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_HISTORY_MESSAGES = 10;   // 保留最近 10 轮（20条消息）
    private static final long HISTORY_TTL_MINUTES = 30;   // 30 分钟无操作则清除记忆

    public String chat(String userMessage) {
        String result = null;
        String status = "SUCCESS";

        try {
            // 1. 获取当前用户手机号（用户名）
            String userId = getCurrentUserId();

            // 2. 从 Redis 获取历史消息
            String historyKey = "chat:history:" + userId;
            List<ChatMessage> history = getHistory(historyKey);

            // 3. 构建完整的消息列表（历史 + 当前用户消息）
            List<AgnesChatRequest.Message> messages = new ArrayList<>();
            for (ChatMessage msg : history) {
                messages.add(new AgnesChatRequest.Message(msg.getRole(), msg.getContent()));
            }
            messages.add(new AgnesChatRequest.Message("user", userMessage));

            // 4. 调用 Agnes API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = getCurrentUserApiKey();
            headers.setBearerAuth(apiKey);

            AgnesChatRequest body = new AgnesChatRequest();
            body.setModel(model);
            body.setMessages(messages);   // 这里传入完整历史 + 当前消息

            HttpEntity<AgnesChatRequest> entity = new HttpEntity<>(body, headers);
            ResponseEntity<AgnesChatResponse> response = restTemplate.postForEntity(
                    apiUrl, entity, AgnesChatResponse.class);

            // 5. 提取回复
            AgnesChatResponse respBody = response.getBody();
            if (respBody != null && respBody.getChoices() != null && !respBody.getChoices().isEmpty()) {
                result = respBody.getChoices().get(0).getMessage().getContent();
            } else {
                result = "No response";
                status = "FAILED";
            }

            // 6. 更新历史（仅在调用成功后记录）
            if ("SUCCESS".equals(status)) {
                history.add(new ChatMessage("user", userMessage));
                history.add(new ChatMessage("assistant", result));
                // 修剪历史长度
                if (history.size() > MAX_HISTORY_MESSAGES) {
                    history = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
                }
                saveHistory(historyKey, history);
            }

        } catch (Exception e) {
            status = "FAILED";
            result = "请求失败：" + e.getMessage();
            throw e;
        } finally {
            logService.log("CHAT", "用户对话", userMessage, status, result);
        }

        return result;
    }


    private String getCurrentUserId() {
        // 优先从 ThreadLocal 获取
        String user = RequestContext.getCurrentUser();
        if (user != null) return user;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();  // 手机号
        }
        throw new RuntimeException("未登录");
    }

    private List<ChatMessage> getHistory(String key) {
        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null) return new ArrayList<>();
        return raw.stream()
                .map(obj -> (ChatMessage) obj)
                .collect(Collectors.toList());
    }

    private void saveHistory(String key, List<ChatMessage> history) {
        redisTemplate.delete(key);   // 先清空
        redisTemplate.opsForList().rightPushAll(key, (Object[]) history.toArray(new Object[0]));
        redisTemplate.expire(key, Duration.ofMinutes(HISTORY_TTL_MINUTES));
    }


    private String getCurrentUserApiKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String phone = auth.getName();
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        try {
            return aesUtil.decrypt(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥");
        }
    }
}
