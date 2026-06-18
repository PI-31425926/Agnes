package com.bilibili.pojo.dto;

import lombok.Data;

@Data
public class ImageGenerationRequest {
    private String prompt;
    private String size = "1024x768";   // 默认尺寸
}
