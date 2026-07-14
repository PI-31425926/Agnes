package com.bilibili.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgnesChatRequest {
    private String model;
    private List<Message> messages;
    private String conversationId;

    @Data
    public static class Message {
        private String role;
        private Object content;  // String (text) or List<Map> (vision format)

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }
    }
}
