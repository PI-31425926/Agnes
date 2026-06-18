package com.bilibili.controller;

import com.bilibili.pojo.dto.ImageGenerationRequest;
import com.bilibili.pojo.dto.ImageGenerationResponse;
import com.bilibili.pojo.dto.ImageToImageRequest;
import com.bilibili.service.AgnesImageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private final AgnesImageService agnesImageService;

    public ImageController(AgnesImageService agnesImageService) {
        this.agnesImageService = agnesImageService;
    }

    @PostMapping
    public ImageGenerationResponse generateImage(@RequestBody ImageGenerationRequest request) throws Exception {
        String url = agnesImageService.generateImage(request.getPrompt(), request.getSize());
        ImageGenerationResponse response = new ImageGenerationResponse();
        response.setUrl(url);
        return response;
    }

    @PostMapping("/to-image")
    public ImageGenerationResponse generateImageFromImage(@RequestBody ImageToImageRequest request) throws Exception {
        String url = agnesImageService.generateImageFromImage(
                request.getPrompt(),
                request.getSize(),
                request.getImageBase64()
        );
        ImageGenerationResponse response = new ImageGenerationResponse();
        response.setUrl(url);
        return response;
    }
}
