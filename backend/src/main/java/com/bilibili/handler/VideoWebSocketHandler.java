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

import org.springframework.scheduling.annotation.Scheduled;

@Component
public class VideoWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // userId (phone) -> set of sessions
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    // userId -> session last access time (manual tracking, since WebSocketSession lacks getLastAccessTime)
    private final Map<String, Long> sessionLastAccess = new ConcurrentHashMap<>();

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
            // 使用 Sa-Token 真实验证 token 有效性（不修改当前线程登录状态）
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                log.warn("WebSocket token invalid, closing");
                closeSession(session, new CloseStatus(401, "Unauthorized"));
                return;
            }
            String userId = loginId.toString();

            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
            sessionLastAccess.put(userId, System.currentTimeMillis());
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
            sessionLastAccess.put(userId, System.currentTimeMillis());
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

    /**
     * 每 5 分钟清理一次：移除已关闭或 5 分钟未活动的 session
     */
    @Scheduled(fixedDelay = 300000)
    public void cleanExpiredSessions() {
        long threshold = System.currentTimeMillis() - 5 * 60 * 1000;
        for (Map.Entry<String, Set<WebSocketSession>> entry : userSessions.entrySet()) {
            String userId = entry.getKey();
            Set<WebSocketSession> sessions = entry.getValue();
            sessions.removeIf(session -> {
                if (!session.isOpen()) {
                    log.debug("Cleaning closed session: sessionId={}", session.getId());
                    return true;
                }
                return false;
            });
            // 清理 5 分钟未活动的用户
            Long lastAccess = sessionLastAccess.get(userId);
            if (lastAccess != null && lastAccess < threshold && sessions.isEmpty()) {
                userSessions.remove(userId);
                sessionLastAccess.remove(userId);
            }
        }
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
