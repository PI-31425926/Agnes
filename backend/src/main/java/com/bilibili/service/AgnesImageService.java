package com.bilibili.service;

import com.bilibili.mapper.UserRepository;
import com.bilibili.pojo.dto.AgnesImageRequest;
import com.bilibili.pojo.dto.AgnesImageResponse;
import com.bilibili.pojo.dto.AgnesImageToImageRequest;
import com.bilibili.pojo.entity.User;
import com.bilibili.utils.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class AgnesImageService {

    /*@Value("${agnes.api-key}")
    private String apiKey;*/

    @Value("${agnes.image-api-url}")
    private String imageApiUrl;

    @Value("${agnes.image-model}")
    private String imageModel;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private LogService logService;

    private final RestTemplate restTemplate;

    public AgnesImageService() {
        this.restTemplate = new RestTemplate();
    }

    public String generateImage(String prompt, String size) throws Exception {
        String url = null;
        String status = "SUCCESS";
        try {
            // 1. 请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = getCurrentUserApiKey();
            headers.setBearerAuth(apiKey);

            // 2. 请求体
            AgnesImageRequest body = new AgnesImageRequest();
            body.setModel(imageModel);
            body.setPrompt(prompt);
            body.setSize(size);

            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("response_format", "url");
            body.setExtraBody(extraBody);

            HttpEntity<AgnesImageRequest> entity = new HttpEntity<>(body, headers);

            // 3. 发送请求
            ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                    imageApiUrl, entity, AgnesImageResponse.class
            );

            // 4. 提取第一张图片的 URL
            AgnesImageResponse respBody = response.getBody();
            if (respBody != null && respBody.getData() != null && !respBody.getData().isEmpty()) {
                url = respBody.getData().get(0).getUrl();
            } else {
                throw new Exception("Image generation failed: no data returned");
            }
        } catch (Exception e) {
            status = "FAILED";
            url = e.getMessage();
            throw e;
        } finally {
            logService.log("IMAGE_GENERATION", "文生图", prompt, status, url);
        }
        return url;
    }

    public String generateImageFromImage(String prompt, String size, String imageBase64) throws Exception {
        String url = null;
        String status = "SUCCESS";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = getCurrentUserApiKey();
            headers.setBearerAuth(apiKey);

            AgnesImageToImageRequest body = new AgnesImageToImageRequest();
            body.setModel(imageModel);
            body.setPrompt(prompt);
            body.setSize(size);

            Map<String, Object> extraBody = new HashMap<>();
            extraBody.put("response_format", "url");
            extraBody.put("image", Collections.singletonList(imageBase64));
            body.setExtraBody(extraBody);

            HttpEntity<AgnesImageToImageRequest> entity = new HttpEntity<>(body, headers);

            ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                    imageApiUrl, entity, AgnesImageResponse.class
            );

            AgnesImageResponse respBody = response.getBody();
            if (respBody != null && respBody.getData() != null && !respBody.getData().isEmpty()) {
                url = respBody.getData().get(0).getUrl();
            } else {
                throw new Exception("Image-to-image generation failed: no data returned");
            }
        } catch (Exception e) {
            status = "FAILED";
            url = e.getMessage();
            throw e;
        } finally {
            logService.log("IMAGE_TO_IMAGE", "图生图", prompt, status, url);
        }
        return url;
    }


    /*public String generateImage(String prompt, String size) throws Exception {
        // 1. 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = getCurrentUserApiKey();
        headers.setBearerAuth(apiKey);

        // 2. 请求体
        AgnesImageRequest body = new AgnesImageRequest();
        body.setModel(imageModel);
        body.setPrompt(prompt);
        body.setSize(size);

        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("response_format", "url");
        body.setExtraBody(extraBody);

        HttpEntity<AgnesImageRequest> entity = new HttpEntity<>(body, headers);

        // 3. 发送请求
        ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                imageApiUrl, entity, AgnesImageResponse.class
        );

        // 4. 提取第一张图片的 URL
        AgnesImageResponse respBody = response.getBody();
        if (respBody != null && respBody.getData() != null && !respBody.getData().isEmpty()) {
            return respBody.getData().get(0).getUrl();
        }
        throw new Exception("Image generation failed: no data returned");
    }

    public String generateImageFromImage(String prompt, String size, String imageBase64) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = getCurrentUserApiKey();
        headers.setBearerAuth(apiKey);

        AgnesImageToImageRequest body = new AgnesImageToImageRequest();
        body.setModel(imageModel);  // 图生图也用同样模型
        body.setPrompt(prompt);
        body.setSize(size);

        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("response_format", "url");
        // 图片数组，里面是一个 data URI
        extraBody.put("image", Collections.singletonList(imageBase64));
        body.setExtraBody(extraBody);

        HttpEntity<AgnesImageToImageRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<AgnesImageResponse> response = restTemplate.postForEntity(
                imageApiUrl, entity, AgnesImageResponse.class
        );

        AgnesImageResponse respBody = response.getBody();
        if (respBody != null && respBody.getData() != null && !respBody.getData().isEmpty()) {
            return respBody.getData().get(0).getUrl();
        }
        throw new Exception("Image-to-image generation failed: no data returned");
    }
*/
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
