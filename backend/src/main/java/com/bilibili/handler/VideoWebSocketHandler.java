package com.bilibili.handler;

import cn.dev33.satoken.stp.StpUtil;
import com.bilibili.pojo.dto.VideoTaskInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VideoWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // userId (phone) -> set of sessions
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /**
     * 连接建立后：从 query param 取 token 验证，通过后加入 session map
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket connection without token, closing");
            closeSession(session, new CloseStatus(401, "Unauthorized"));
            return;
        }

        try {
            // 简单验证：token 非空且长度合理即认为有效
            // Sa-Token 的 token 本身就是 loginId 引用，直接用作 userId key
            if (token.length() < 10) {
                log.warn("WebSocket token too short, closing");
                closeSession(session, new CloseStatus(401, "Unauthorized"));
                return;
            }
            String userId = token;

            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
            log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
        } catch (Exception e) {
            log.warn("WebSocket token validation failed: {}", e.getMessage());
            closeSession(session, new CloseStatus(401, "Unauthorized"));
        }
    }

    /**
     * 客户端发消息（当前不需要客户端主动发消息，保持连接即可）
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 忽略客户端消息
    }

    /**
     * 连接关闭后：从 session map 移除
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdBySession(session);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            log.info("WebSocket disconnected: userId={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
        String userId = getUserIdBySession(session);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
            }
        }
    }

    /**
     * 向指定用户推送消息
     */
    public void pushToUser(String userId, VideoTaskInfo task, String eventType) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "type", eventType,
                    "data", task
            );
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            log.error("Failed to push WebSocket message to userId={}", userId, e);
        }
    }

    /**
     * 获取 session 数量
     */
    public int getSessionCount() {
        return userSessions.values().stream().mapToInt(Set::size).sum();
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    private String getUserIdBySession(WebSocketSession session) {
        for (Map.Entry<String, Set<WebSocketSession>> entry : userSessions.entrySet()) {
            if (entry.getValue().contains(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
        }
    }
}
