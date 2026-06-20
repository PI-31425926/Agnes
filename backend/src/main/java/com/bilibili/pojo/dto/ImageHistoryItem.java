package com.bilibili.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageHistoryItem {
    private String type;      // "text2img" 或 "img2img"
    private String prompt;
    private String url;
    private long timestamp;   // 生成时间戳
}