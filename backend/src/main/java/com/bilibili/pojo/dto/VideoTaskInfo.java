package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoTaskInfo {
    private String taskId;
    private String videoId;
    private String prompt;        // 用于前端展示
    private String status;        // queued/processing/completed/failed
    private int progress;
    private String url;
    private String error;
    private long createdAt;       // 时间戳，可忽略
    @JsonIgnore
    private String userId;        // 新增：任务归属用户（手机号）
    private String apiKey;  // 新增，用于轮询时调用 Agnes API
}
