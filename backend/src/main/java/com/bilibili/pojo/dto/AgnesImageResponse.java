package com.bilibili.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgnesImageResponse {
    private List<ImageData> data;

    @Data
    public static class ImageData {
        private String url;
        private String b64_json;
        private String revised_prompt;
    }
}
