package com.bilibili.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final com.bilibili.handler.VideoWebSocketHandler videoWebSocketHandler;

    public WebSocketConfig(com.bilibili.handler.VideoWebSocketHandler videoWebSocketHandler) {
        this.videoWebSocketHandler = videoWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(videoWebSocketHandler, "/api/ws/video")
                .setAllowedOrigins("*");
    }
}
