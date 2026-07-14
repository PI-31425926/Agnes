package com.bilibili.service;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesVideoCreateRequest;
import com.bilibili.pojo.dto.AgnesVideoCreateResponse;
import com.bilibili.pojo.dto.AgnesVideoStatusResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AgnesVideoService {
    @Value("${agnes.video-api-url}")
    private String videoApiUrl;

    @Value("${agnes.video-status-url}")
    private String videoStatusUrl;

    @Value("${agnes.video-model}")
    private String videoModel;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private LogService logService;

    private final RestTemplate restTemplate;

    public AgnesVideoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 创建视频任务（用户操作，纯文生视频）
     */
    public AgnesVideoCreateResponse createVideoTask(String prompt, int width, int height, int numFrames, int frameRate, String apiKey) {
        return createVideoTask(prompt, width, height, numFrames, frameRate, (String) null, (Map<String, Object>) null, "ti2vid", apiKey);
    }

    /**
     * 创建视频任务（完整重载，支持图生视频 / 关键帧）
     */
    public AgnesVideoCreateResponse createVideoTask(
            String prompt, int width, int height, int numFrames, int frameRate,
            String imageUrl, Map<String, Object> extraBody, String mode, String apiKey) {
        AgnesVideoCreateResponse result = null;
        String status = "SUCCESS";
        String errorMsg = null;
        String userId = StpUtil.getLoginIdAsString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            AgnesVideoCreateRequest body = buildVideoRequestBody(prompt, width, height, numFrames, frameRate, imageUrl, extraBody, mode);

            HttpEntity<AgnesVideoCreateRequest> entity = new HttpEntity<>(body, headers);

            ResponseEntity<AgnesVideoCreateResponse> response = restTemplate.postForEntity(
                    videoApiUrl, entity, AgnesVideoCreateResponse.class);

            result = response.getBody();
            if (result == null || result.getVideoId() == null) {
                throw new RuntimeException("上游API返回空响应: " + (result != null ? result.toString() : "null"));
            }
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            throw new RuntimeException("视频任务创建失败: " + errorMsg, e);
        } finally {
            // 记录日志
            String modeLabel = "ti2vid".equals(mode) ? "文生视频" : "i2vid".equals(mode) ? "图生视频" : "关键帧动画";
            String description = String.format("%s：%dx%d, %d帧, %dfps", modeLabel, width, height, numFrames, frameRate);
            String resultDetail = result != null ? result.getVideoId() : errorMsg;
            logService.log("VIDEO_GENERATION", description, prompt, status, resultDetail, userId);
        }
        return result;
    }

    /**
     * 根据模式和图片参数组装视频生成请求体
     */
    private AgnesVideoCreateRequest buildVideoRequestBody(
            String prompt, int width, int height, int numFrames, int frameRate,
            String imageUrl, Map<String, Object> extraBody, String mode) {
        AgnesVideoCreateRequest request = new AgnesVideoCreateRequest();
        request.setModel(videoModel);
        request.setPrompt(prompt);
        request.setWidth(width);
        request.setHeight(height);
        request.setNumFrames(numFrames);
        request.setFrameRate(frameRate);

        if ("keyframes".equals(mode) && extraBody != null) {
            request.setMode(mode);
            request.setExtraBody(extraBody);
        } else if ("i2vid".equals(mode) && imageUrl != null) {
            request.setImage(imageUrl);
            // mode 字段对于图生视频需要省略
        } else {
            // 文生视频：mode 字段也需要省略
        }

        return request;
    }

    /**
     * 图生视频（便捷方法）
     */
    public AgnesVideoCreateResponse createVideoTaskWithImage(
            String prompt, String imageUrl, int width, int height, int numFrames, int frameRate, String apiKey) {
        return createVideoTask(prompt, width, height, numFrames, frameRate, imageUrl, null, "i2vid", apiKey);
    }

    /**
     * 关键帧动画（便捷方法）
     */
    public AgnesVideoCreateResponse createVideoTaskWithKeyframes(
            String prompt, Map<String, Object> extraBody, int width, int height, int numFrames, int frameRate, String apiKey) {
        return createVideoTask(prompt, width, height, numFrames, frameRate, null, extraBody, "keyframes", apiKey);
    }

    /**
     * 查询视频状态（内部轮询，不记录用户日志）
     */
    public AgnesVideoStatusResponse queryVideoStatus(String videoId) {
        HttpHeaders headers = new HttpHeaders();
        String apiKey = getCurrentUserApiKey();
        headers.setBearerAuth(apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = videoStatusUrl + "?video_id=" + videoId;
        ResponseEntity<AgnesVideoStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, AgnesVideoStatusResponse.class);

        return response.getBody();
    }

    private String getCurrentUserApiKey() {
        String phone = StpUtil.getLoginIdAsString();
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        try {
            return aesUtil.decryptLegacy(user.getApiKey());
        } catch (Exception e) {
            throw new RuntimeException("无法解密API密钥");
        }
    }

    public AgnesVideoStatusResponse queryVideoStatusWithApiKey(String videoId, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = videoStatusUrl + "?video_id=" + videoId;
        ResponseEntity<AgnesVideoStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, AgnesVideoStatusResponse.class);
        return response.getBody();
    }
}
