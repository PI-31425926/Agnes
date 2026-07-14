package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class VideoGenerationRequest {
    private String prompt;
    /** 生成模式：ti2vid（默认）/ i2vid / keyframes */
    private String mode = "ti2vid";
    /** 图生视频的单图 URL（废弃，使用 imageUrls） */
    private String imageUrl;
    /** 图片 URL 列表（图生视频传单元素数组，关键帧传多元素数组） */
    private List<String> imageUrls;
    private int width = 1152;       // 默认
    private int height = 768;
    private int numFrames = 121;
    private int frameRate = 24;
    private Integer numInferenceSteps;
    private Integer seed;
    @JsonProperty("negative_prompt")
    private String negativePrompt;
}
