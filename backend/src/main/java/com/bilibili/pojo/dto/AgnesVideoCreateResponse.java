package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AgnesVideoCreateResponse {
    private String id;            // task_id
    @JsonProperty("task_id")
    private String taskId;
    @JsonProperty("video_id")
    private String videoId;
    private String object;
    private String model;
    private String status;
    private int progress;
    // ...
}
