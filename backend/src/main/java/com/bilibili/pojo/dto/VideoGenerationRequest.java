package com.bilibili.pojo.dto;

import lombok.Data;

@Data
public class VideoGenerationRequest {
    private String prompt;
    private int width = 1152;       // 默认
    private int height = 768;
    private int numFrames = 121;
    private int frameRate = 24;
}
