package com.bilibili.service.workflow;

import com.bilibili.pojo.dto.AgnesChatRequest;
import com.bilibili.pojo.dto.AgnesChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LLM prompt refinement executor.
 * Calls the chat API with a system prompt designed to enhance/optimize user input.
 */
@Component
public class LlmRefineExecutor implements NodeExecutor {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a professional prompt engineer. The user will give you a short description or prompt. " +
            "Expand it into a detailed, vivid, and creative prompt suitable for content generation. " +
            "Preserve the original intent, add details about atmosphere, style, and composition. " +
            "Output only the refined prompt, no extra explanation.";

    @Value("${agnes.api-url}")
    private String apiUrl;

    @Value("${agnes.model}")
    private String model;

    private final RestTemplate restTemplate;

    public LlmRefineExecutor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String nodeType() {
        return "text_refine";
    }

    @Override
    public ExecutionResult execute(ExecutionContext ctx, Map<String, Object> config) {
        String inputPrompt = resolveString(ctx, config, "prompt");
        if (inputPrompt == null || inputPrompt.isBlank()) {
            return ExecutionResult.failed("Missing required field: prompt");
        }

        String apiKey = getString(config, "api_key");
        String systemPrompt = getString(config, "system_prompt");
        if (systemPrompt == null) systemPrompt = DEFAULT_SYSTEM_PROMPT;

        List<AgnesChatRequest.Message> messages = new ArrayList<>();
        messages.add(new AgnesChatRequest.Message("system", systemPrompt));
        messages.add(new AgnesChatRequest.Message("user", "Please refine this prompt: " + inputPrompt));

        AgnesChatRequest body = new AgnesChatRequest();
        body.setModel(model);
        body.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<AgnesChatResponse> response = restTemplate.postForEntity(
                apiUrl, new HttpEntity<>(body, headers), AgnesChatResponse.class);

        String refined = extractContent(response.getBody());
        if (refined == null) {
            return ExecutionResult.failed("Empty response from LLM");
        }

        Map<String, Object> output = Map.of("refined_prompt", refined);
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
