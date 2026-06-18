package com.bilibili.service;

import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesVideoCreateRequest;
import com.bilibili.pojo.dto.AgnesVideoCreateResponse;
import com.bilibili.pojo.dto.AgnesVideoStatusResponse;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AgnesVideoService {

    /*@Value("${agnes.api-key}")
    private String apiKey;*/

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

    public AgnesVideoService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 创建视频任务（用户操作）
     */
    public AgnesVideoCreateResponse createVideoTask(String prompt, int width, int height, int numFrames, int frameRate, String apiKey) {
        AgnesVideoCreateResponse result = null;
        String status = "SUCCESS";
        String errorMsg = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            //String apiKey = getCurrentUserApiKey();
            headers.setBearerAuth(apiKey);

            AgnesVideoCreateRequest body = new AgnesVideoCreateRequest();
            body.setModel(videoModel);
            body.setPrompt(prompt);
            body.setWidth(width);
            body.setHeight(height);
            body.setNumFrames(numFrames);
            body.setFrameRate(frameRate);

            HttpEntity<AgnesVideoCreateRequest> entity = new HttpEntity<>(body, headers);

            ResponseEntity<AgnesVideoCreateResponse> response = restTemplate.postForEntity(
                    videoApiUrl, entity, AgnesVideoCreateResponse.class);

            result = response.getBody();
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            throw new RuntimeException("视频任务创建失败: " + errorMsg, e);
        } finally {
            // 记录日志
            String description = String.format("文生视频：%dx%d, %d帧, %dfps", width, height, numFrames, frameRate);
            String resultDetail = result != null ? result.getVideoId() : errorMsg;
            logService.log("VIDEO_GENERATION", description, prompt, status, resultDetail);
        }
        return result;
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

    // 创建视频任务
    /*public AgnesVideoCreateResponse createVideoTask(String prompt, int width, int height, int numFrames, int frameRate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = getCurrentUserApiKey();
        headers.setBearerAuth(apiKey);

        AgnesVideoCreateRequest body = new AgnesVideoCreateRequest();
        body.setModel(videoModel);
        body.setPrompt(prompt);
        body.setWidth(width);
        body.setHeight(height);
        body.setNumFrames(numFrames);
        body.setFrameRate(frameRate);

        HttpEntity<AgnesVideoCreateRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<AgnesVideoCreateResponse> response = restTemplate.postForEntity(
                videoApiUrl, entity, AgnesVideoCreateResponse.class);

        return response.getBody();
    }

    // 查询视频状态
    public AgnesVideoStatusResponse queryVideoStatus(String videoId) {
        HttpHeaders headers = new HttpHeaders();
        String apiKey = getCurrentUserApiKey();
        headers.setBearerAuth(apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = videoStatusUrl + "?video_id=" + videoId;
        ResponseEntity<AgnesVideoStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, AgnesVideoStatusResponse.class);

        return response.getBody();
    }*/

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
