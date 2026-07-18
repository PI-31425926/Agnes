package com.bilibili.service.workflow;

import com.bilibili.pojo.dto.AgnesVideoCreateRequest;
import com.bilibili.pojo.dto.AgnesVideoCreateResponse;
import com.bilibili.pojo.dto.AgnesVideoStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Keyframe animation executor. Creates a video from multiple images.
 */
@Component
public class KeyframeAnimationExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(KeyframeAnimationExecutor.class);

    @Value("${agnes.video-api-url}")
    private String videoApiUrl;

    @Value("${agnes.video-model}")
    private String videoModel;

    private final RestTemplate restTemplate;

    public KeyframeAnimationExecutor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String nodeType() {
        return "keyframe_animation";
    }

    @Override
    public ExecutionResult execute(ExecutionContext ctx, Map<String, Object> config) {
        List<String> imageUrls = resolveImageUrls(ctx, config);
        String prompt = resolveString(ctx, config, "prompt");
        if (imageUrls == null || imageUrls.isEmpty()) {
            return ExecutionResult.failed("Missing required field: image_urls (at least one image required)");
        }

        String apiKey = getString(config, "api_key");
        int width = getInt(config, "width", 1152);
        int height = getInt(config, "height", 768);
        int numFrames = getInt(config, "num_frames", 121);
        int frameRate = getInt(config, "frame_rate", 24);

        AgnesVideoCreateRequest body = new AgnesVideoCreateRequest();
        body.setModel(videoModel);
        body.setPrompt(prompt != null ? prompt : "");
        body.setMode("keyframes");

        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("image", imageUrls);
        extraBody.put("mode", "keyframes");
        body.setExtraBody(extraBody);

        body.setWidth(width);
        body.setHeight(height);
        body.setNumFrames(numFrames);
        body.setFrameRate(frameRate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<AgnesVideoCreateResponse> response = restTemplate.postForEntity(
                videoApiUrl, new HttpEntity<>(body, headers), AgnesVideoCreateResponse.class);

        AgnesVideoCreateResponse resp = response.getBody();
        if (resp == null || resp.getVideoId() == null) {
            return ExecutionResult.failed("Empty response from video API");
        }

        Map<String, Object> output = new HashMap<>();
        output.put("video_id", resp.getVideoId());
        output.put("status", resp.getStatus());
        return ExecutionResult.pending(output);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    public ExecutionResult poll(String videoId, String apiKey) {
        String url = "https://apihub.agnes-ai.com/agnesapi?video_id=" + videoId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        ResponseEntity<AgnesVideoStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), AgnesVideoStatusResponse.class);

        AgnesVideoStatusResponse status = response.getBody();
        if (status == null) return ExecutionResult.failed("Empty status response");

        String videoUrl = status.getDownloadUrl();
        if (videoUrl == null || videoUrl.isBlank()) videoUrl = status.getUrl();

        if ("completed".equals(status.getStatus()) && videoUrl != null) {
            return ExecutionResult.success(Map.of("video_url", videoUrl, "status", "completed"));
        }
        if ("failed".equals(status.getStatus())) {
            return ExecutionResult.failed("Video generation failed: " + status.getError());
        }

        return ExecutionResult.pending(Map.of("video_id", videoId, "status", status.getStatus(), "progress", status.getProgress()));
    }

    private List<String> resolveImageUrls(ExecutionContext ctx, Map<String, Object> config) {
        List<String> urls = new ArrayList<>();

        // Collect from ctx: all upstream nodes that have image_url
        for (Map.Entry<String, Map<String, Object>> entry : ctx.getNodeOutputsMap().entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> outputs = entry.getValue();
            for (String field : new String[]{"image_url", "url"}) {
                Object val = outputs.get(field);
                if (val != null && val.toString().trim().length() > 0) {
                    urls.add(val.toString().trim());
                    break; // only take first matching field per node
                }
            }
        }

        // Also check config for explicit image_urls list or comma-separated image_url
        Object configUrls = config.get("image_urls");
        if (configUrls instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    String resolved = resolveReferences(ctx, s);
                    if (!resolved.isBlank()) {
                        for (String u : resolved.split(",")) {
                            String trimmed = u.trim();
                            if (!trimmed.isBlank() && !urls.contains(trimmed)) {
                                urls.add(trimmed);
                            }
                        }
                    }
                }
            }
        } else if (configUrls instanceof String s && !s.isBlank()) {
            String resolved = resolveReferences(ctx, s);
            for (String u : resolved.split(",")) {
                String trimmed = u.trim();
                if (!trimmed.isBlank() && !urls.contains(trimmed)) {
                    urls.add(trimmed);
                }
            }
        }

        log.info("KeyframeAnimationExecutor: resolved {} image URLs: {}", urls.size(), urls);
        return urls;
    }

    private String getString(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v != null ? v.toString() : null;
    }

    private int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }

    private String resolveString(ExecutionContext ctx, Map<String, Object> config, String field) {
        Object val = config.get(field);
        if (val instanceof String str) return resolveReferences(ctx, str);
        return val != null ? val.toString() : null;
    }

    private String resolveReferences(ExecutionContext ctx, String text) {
        if (text == null) return null;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf("${", i);
            if (start < 0) { result.append(text.substring(i)); break; }
            result.append(text.substring(i, start));
            int end = text.indexOf("}", start + 2);
            if (end < 0) { result.append("${"); i = start + 2; continue; }
            Object resolved = ctx.get(text.substring(start + 2, end));
            result.append(resolved != null ? resolved.toString() : "");
            i = end + 1;
        }
        return result.toString();
    }
}
