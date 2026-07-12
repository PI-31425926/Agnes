package com.bilibili.pojo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String role;
    private String content;

    @JsonCreator
    public static ChatMessage of(@JsonProperty("role") String role, @JsonProperty("content") String content) {
        return new ChatMessage(role, content);
    }
}
