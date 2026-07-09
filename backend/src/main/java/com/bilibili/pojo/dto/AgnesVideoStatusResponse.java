package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AgnesVideoStatusResponse {
    private String id;
    @JsonProperty("video_id")
    private String videoId;
    private String model;
    private String object;
    private String status;
    private int progress;
    @JsonProperty("remixed_from_video_id")
    private String remixedFromVideoId;
    @JsonProperty("download_url")
    private String downloadUrl;
    @JsonProperty("result")
    private String result;
    private String url;
    private String error;
    // 其它字段可忽略
}
