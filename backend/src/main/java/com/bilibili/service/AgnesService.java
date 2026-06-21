package com.bilibili.service;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.common.context.RequestContext;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesChatRequest;
import com.bilibili.pojo.dto.AgnesChatResponse;
import com.bilibili.pojo.dto.ChatMessage;
import com.bilibili.pojo.dto.ChatResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AgnesService {
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
        throw new RuntimeException("未登录");
    }

    public List<ChatMessage> getHistory(String key) {
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
        // 1. 从 Sa-Token 获取当前登录的手机号
        String phone = StpUtil.getLoginIdAsString();   // 确保已登录，否则会抛 NotLoginException
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        try {
            return aesUtil.decrypt(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥");
        }
    }

    public void chatStreamReal(final String userMessage, SseEmitter emitter, String userId, String userApiKey) {
        CompletableFuture.runAsync(() -> {
            String fullReply = null;
            String status = "SUCCESS";
            // 用于日志记录的原始用户消息（不含文档拼接内容）
            String originalUserMessage = userMessage;
            try {
                System.out.println("[Stream] 开始处理, userId=" + userId);

                // -------------------- 文件暂存拼接 --------------------
                String fileKey = "file:content:" + userId;
                String fileContent = (String) redisTemplate.opsForValue().get(fileKey);
                String userMessage2 = null;
                if (fileContent != null && !fileContent.isEmpty()) {
                    // 用户输入为空时使用默认问题
                    String userQuestion = (userMessage != null && !userMessage.trim().isEmpty())
                            ? userMessage : "请总结以下文档内容";
                    userMessage2 = userQuestion + "\n\n文档内容：\n" + fileContent;
                }
                // ---------------------------------------------------------

                // 1. 获取历史（使用传入的 userId）
                String historyKey = "chat:history:" + userId;
                List<ChatMessage> history = getHistory(historyKey);

                List<AgnesChatRequest.Message> messages = new ArrayList<>();
                for (ChatMessage msg : history) {
                    messages.add(new AgnesChatRequest.Message(msg.getRole(), msg.getContent()));
                }
                messages.add(new AgnesChatRequest.Message("user", userMessage2)); // 使用拼接后的消息

                // 2. 构建请求头（使用传入的 apiKey）
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(userApiKey);

                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("model", model);
                bodyMap.put("messages", messages);
                bodyMap.put("stream", true);

                ObjectMapper mapper = new ObjectMapper();
                byte[] bodyBytes = mapper.writeValueAsBytes(bodyMap);

                // 3. 执行流式请求
                StringBuilder fullReplyBuilder = new StringBuilder();
                restTemplate.execute(apiUrl, HttpMethod.POST,
                        request -> {
                            request.getHeaders().putAll(headers);
                            request.getBody().write(bodyBytes);
                        },
                        clientHttpResponse -> {
                            InputStream inputStream = clientHttpResponse.getBody();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if ("[DONE]".equals(data.trim())) break;
                                    try {
                                        JsonNode node = mapper.readTree(data);
                                        JsonNode choices = node.path("choices");
                                        if (choices.isArray() && choices.size() > 0) {
                                            JsonNode delta = choices.get(0).path("delta");
                                            String content = delta.path("content").asText();
                                            if (!content.isEmpty()) {
                                                fullReplyBuilder.append(content);
                                                emitter.send(SseEmitter.event()
                                                        .data(content)
                                                        .name("message"));
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                            emitter.send(SseEmitter.event().data("[DONE]").name("message"));
                            return fullReplyBuilder.toString();
                        });

                fullReply = fullReplyBuilder.toString();

                // 4. 更新历史（记录拼接后的用户消息，以保持上下文一致性）
                if (fullReply == null || fullReply.isEmpty()) {
                    fullReply = "No response";
                    status = "FAILED";
                } else {
                    history.add(new ChatMessage("user", userMessage2));
                    history.add(new ChatMessage("assistant", fullReply));
                    if (history.size() > MAX_HISTORY_MESSAGES) {
                        history = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
                    }
                    saveHistory(historyKey, history);
                }

                // 5. 对话成功后清除暂存文件（无论成功失败可选，推荐成功时清除）
                if ("SUCCESS".equals(status)) {
                    redisTemplate.delete(fileKey);   // 清除 Redis 中的文件内容
                }

                emitter.complete();
                System.out.println("[Stream] 完成");

            } catch (Exception e) {
                status = "FAILED";
                fullReply = "请求失败：" + e.getMessage();
                System.err.println("[Stream] 异常: " + e.getMessage());
                emitter.completeWithError(e);
            } finally {
                // 日志记录使用原始用户消息（不含文档内容），便于审计且避免日志过长
                logService.log("CHAT", "用户对话（流式）", originalUserMessage, status,
                        status.equals("SUCCESS") ? fullReply : null,userId);
            }
        });
    }

    public String chatWithApiKey(String message, String apiKey, String phone) {
        String fullReply = null;
        String status = "SUCCESS";
        String originalUserMessage = message;
        try {
            System.out.println("[Guest Chat] 用户: " + phone);

            // 构建消息：仅当前用户消息，无历史
            List<AgnesChatRequest.Message> messages = new ArrayList<>();
            messages.add(new AgnesChatRequest.Message("user", message));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            AgnesChatRequest body = new AgnesChatRequest();
            body.setModel(model);
            body.setMessages(messages);

            HttpEntity<AgnesChatRequest> entity = new HttpEntity<>(body, headers);
            ResponseEntity<AgnesChatResponse> response = restTemplate.postForEntity(
                    apiUrl, entity, AgnesChatResponse.class);

            AgnesChatResponse respBody = response.getBody();
            if (respBody != null && respBody.getChoices() != null && !respBody.getChoices().isEmpty()) {
                fullReply = respBody.getChoices().get(0).getMessage().getContent();
            } else {
                fullReply = "No response";
                status = "FAILED";
            }

            return fullReply;
        } catch (Exception e) {
            status = "FAILED";
            fullReply = "请求失败：" + e.getMessage();
            System.err.println("[Guest Chat] 异常: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            // 记录日志（使用随机用户的手机号，便于追踪）
            logService.log("CHAT", "游客对话", originalUserMessage, status,
                    status.equals("SUCCESS") ? fullReply : null, phone);
        }
    }
}
