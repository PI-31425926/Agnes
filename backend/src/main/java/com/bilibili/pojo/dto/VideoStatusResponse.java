package com.bilibili.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoStatusResponse {
    private String videoId;
    private String status;        // queued / processing / completed / failed
    private int progress;         // 0-100
    private String url;           // 视频地址，完成后返回
    private String error;
}
