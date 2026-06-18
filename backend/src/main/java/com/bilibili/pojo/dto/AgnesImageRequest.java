package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AgnesImageRequest {
    private String model;
    private String prompt;
    private String size;

    @JsonProperty("extra_body")
    private Map<String, Object> extraBody;
}
