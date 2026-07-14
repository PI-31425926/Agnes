package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgnesVideoCreateRequest {
    private String model;
    private String prompt;
    /** 图生视频使用的图片 URL（单图） */
    private String image;
    /** 生成模式：ti2vid / i2vid / keyframes */
    private String mode;
    private int height;
    private int width;
    @JsonProperty("num_frames")
    private int numFrames;
    @JsonProperty("frame_rate")
    private int frameRate;
    @JsonProperty("num_inference_steps")
    private Integer numInferenceSteps;
    private Integer seed;
    @JsonProperty("negative_prompt")
    private String negativePrompt;
    /** 关键帧附加参数（包含 image 数组和 mode） */
    @JsonProperty("extra_body")
    private Object extraBody;
}
