package com.bilibili.service.workflow;

import com.bilibili.pojo.dto.AgnesChatRequest;
import com.bilibili.pojo.dto.AgnesChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Image understanding executor.
 * Calls the chat API with vision capability to describe or analyze an image.
 */
@Component
public class ImageUnderstandExecutor implements NodeExecutor {

    @Value("${agnes.api-url}")
    private String apiUrl;

    @Value("${agnes.model}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImageUnderstandExecutor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String nodeType() {
        return "image_understand";
    }

    @Override
    public ExecutionResult execute(ExecutionContext ctx, Map<String, Object> config) {
        String imageUrl = resolveString(ctx, config, "image_url");
        String prompt = resolveString(ctx, config, "prompt");
        if (imageUrl == null || imageUrl.isBlank()) {
            return ExecutionResult.failed("Missing required field: image_url");
        }
        if (prompt == null || prompt.isBlank()) {
            prompt = "Please describe this image in detail.";
        }

        String apiKey = getString(config, "api_key");

        // Build vision-format content array
        ArrayNode contentArray = mapper.createArrayNode();

        ObjectNode textPart = contentArray.addObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        ObjectNode imagePart = contentArray.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageNode = imagePart.putObject("image_url");
        imageNode.put("url", imageUrl);

        List<AgnesChatRequest.Message> messages = List.of(
                new AgnesChatRequest.Message("user", contentArray)
        );

        AgnesChatRequest body = new AgnesChatRequest();
        body.setModel(model);
        body.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("model", model);
        bodyMap.put("messages", messages);

        ResponseEntity<AgnesChatResponse> response = restTemplate.postForEntity(
                apiUrl, new HttpEntity<>(body, headers), AgnesChatResponse.class);

        String description = extractContent(response.getBody());
        if (description == null) {
            return ExecutionResult.failed("Empty response from LLM");
        }

        Map<String, Object> output = Map.of("description", description);
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
