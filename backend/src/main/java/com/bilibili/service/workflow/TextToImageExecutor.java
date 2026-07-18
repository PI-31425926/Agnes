package com.bilibili.service.workflow;

import com.bilibili.pojo.dto.AgnesImageRequest;
import com.bilibili.pojo.dto.AgnesImageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Text-to-image executor.
 * Calls the image generation API with a text prompt.
 */
@Component
public class TextToImageExecutor implements NodeExecutor {

    @Value("${agnes.image-api-url}")
    private String imageApiUrl;

    @Value("${agnes.image-model}")
    private String imageModel;

    private final RestTemplate restTemplate;

    public TextToImageExecutor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String nodeType() {
        return "text_to_image";
    }

    @Override
    public ExecutionResult execute(ExecutionContext ctx, Map<String, Object> config) {
        String prompt = resolveString(ctx, config, "prompt");
        if (prompt == null || prompt.isBlank()) {
            return ExecutionResult.failed("Missing required field: prompt");
        }

        String apiKey = getString(config, "api_key");
        String size = getString(config, "size");
        if (size == null) size = "1024x768";

        AgnesImageRequest body = new AgnesImageRequest();
        body.setModel(imageModel);
        body.setPrompt(prompt);
        body.setSize(size);

        Map<String, Object> extra = new HashMap<>();
        extra.put("response_format", "url");
        body.setExtraBody(extra);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                imageApiUrl, new HttpEntity<>(body, headers), AgnesImageResponse.class);

        String url = extractUrl(response.getBody());
        if (url == null) {
            return ExecutionResult.failed("No image URL in response");
        }

        Map<String, Object> output = Map.of("image_url", url);
        return ExecutionResult.success(output);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    private String extractUrl(AgnesImageResponse response) {
        if (response == null || response.getData() == null || response.getData().isEmpty()) return null;
        AgnesImageResponse.ImageData data = response.getData().get(0);
        return data.getUrl();
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
