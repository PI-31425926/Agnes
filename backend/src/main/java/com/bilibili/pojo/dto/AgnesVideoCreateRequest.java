package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AgnesVideoCreateRequest {
    private String model;
    private String prompt;
    private int height;
    private int width;
    @JsonProperty("num_frames")
    private int numFrames;
    @JsonProperty("frame_rate")
    private int frameRate;
}
