package com.bilibili.pojo.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
    private String imageUrl;  // 图片识别：OSS 公网 URL
}
