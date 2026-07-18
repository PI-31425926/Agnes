package com.bilibili.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final com.bilibili.handler.VideoWebSocketHandler videoWebSocketHandler;
    private final com.bilibili.handler.WorkflowWebSocketHandler workflowWebSocketHandler;

    public WebSocketConfig(com.bilibili.handler.VideoWebSocketHandler videoWebSocketHandler,
                           com.bilibili.handler.WorkflowWebSocketHandler workflowWebSocketHandler) {
        this.videoWebSocketHandler = videoWebSocketHandler;
        this.workflowWebSocketHandler = workflowWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(videoWebSocketHandler, "/api/ws/video")
                .setAllowedOrigins("*");
        registry.addHandler(workflowWebSocketHandler, "/api/ws/workflow")
                .setAllowedOrigins("*");
    }
}
