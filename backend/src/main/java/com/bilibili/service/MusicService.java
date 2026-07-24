package com.bilibili.service;

import com.bilibili.pojo.dto.MusicRequest;
import com.bilibili.pojo.dto.MusicResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
public class MusicService {

    private final String musicApiUrl;
    private final int timeoutMs;
    private final RestTemplate restTemplate;

    public MusicService(
            @Value("${music.api-url}") String musicApiUrl,
            @Value("${music.timeout:30000}") int timeoutMs) {
        this.musicApiUrl = musicApiUrl;
        this.timeoutMs = timeoutMs;
        this.restTemplate = new RestTemplate();
    }

    public MusicResponse generate(MusicRequest request) throws Exception {
        String url = musicApiUrl + "/api/generate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 设置默认值
        if (request.getOutputLength() == null) request.setOutputLength(128);
        if (request.getTemperature() == null) request.setTemperature(0.5);
        if (request.getModelType() == null) request.setModelType("rnn");
        if (request.getInstrument() == null) request.setInstrument("piano");
        if (request.getKey() == null) request.setKey("G");
        if (request.getBpm() == null) request.setBpm(120);
        if (request.getReturnMidi() == null) request.setReturnMidi(true);
        if (request.getInputText() == null) request.setInputText("");

        HttpEntity<MusicRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                MusicResponse musicResponse = new MusicResponse();
                musicResponse.setGeneratedText((String) body.get("generated_text"));
                musicResponse.setMidiBase64((String) body.get("midi_base64"));
                musicResponse.setModelType((String) body.get("model_type"));
                musicResponse.setStyle((String) body.get("style"));
                return musicResponse;
            }

            throw new Exception("Music service returned error: " + response.getStatusCode());
        } catch (RestClientException e) {
            throw new Exception("无法连接到音乐生成服务 (" + musicApiUrl + "): " + e.getMessage(), e);
        }
    }
}
