package com.bilibili.service.workflow;

import com.bilibili.pojo.dto.AgnesChatRequest;
import com.bilibili.pojo.dto.AgnesChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Free-form text chat executor.
 * Calls the chat API for general conversation.
 */
@Component
public class TextChatExecutor implements NodeExecutor {

    @Value("${agnes.api-url}")
    private String apiUrl;

    @Value("${agnes.model}")
    private String model;

    private final RestTemplate restTemplate;

    public TextChatExecutor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String nodeType() {
        return "text_chat";
    }

    @Override
    public ExecutionResult execute(ExecutionContext ctx, Map<String, Object> config) {
        String message = resolveString(ctx, config, "prompt");
        if (message == null || message.isBlank()) {
            return ExecutionResult.failed("Missing required field: prompt");
        }

        String apiKey = getString(config, "api_key");
        String systemPrompt = getString(config, "system_prompt");

        List<AgnesChatRequest.Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new AgnesChatRequest.Message("system", systemPrompt));
        }
        messages.add(new AgnesChatRequest.Message("user", message));

        AgnesChatRequest body = new AgnesChatRequest();
        body.setModel(model);
        body.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<AgnesChatResponse> response = restTemplate.postForEntity(
                apiUrl, new HttpEntity<>(body, headers), AgnesChatResponse.class);

        String reply = extractContent(response.getBody());
        if (reply == null) {
            return ExecutionResult.failed("Empty response from LLM");
        }

        Map<String, Object> output = Map.of("response", reply);
        return ExecutionResult.success(output);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    private String extractContent(AgnesChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) return null;
        Object content = response.getChoices().get(0).getMessage().getContent();
        return content != null ? content.toString() : null;
    }

    private String getString(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v != null ? v.toString() : null;
    }

    private String resolveString(ExecutionContext ctx, Map<String, Object> config, String field) {
        Object val = config.get(field);
        if (val instanceof String str) {
            return resolveReferences(ctx, str);
        }
        return val != null ? val.toString() : null;
    }

    private String resolveReferences(ExecutionContext ctx, String text) {
        if (text == null) return null;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("${", i);
            if (start < 0) {
                result.append(text.substring(i));
                break;
            }
            result.append(text.substring(i, start));
            int end = text.indexOf("}", start + 2);
            if (end < 0) {
                result.append("${");
                i = start + 2;
                continue;
            }
            String ref = text.substring(start + 2, end);
            Object resolved = ctx.get(ref);
            result.append(resolved != null ? resolved.toString() : "");
            i = end + 1;
        }
        return result.toString();
    }
}
