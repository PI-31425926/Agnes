package com.bilibili.pojo.dto;

import lombok.Data;

@Data
public class ImageToImageRequest {
    private String prompt;
    private String size = "1024x768";
    private String imageBase64;  // 完整的 data:image/png;base64,xxxx
}
