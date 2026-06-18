package com.bilibili.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoTaskResponse {
    private String taskId;
    private String videoId;
    private String status;
    private int progress;
}
