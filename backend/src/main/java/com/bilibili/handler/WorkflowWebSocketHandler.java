package com.bilibili.handler;

import cn.dev33.satoken.stp.StpUtil;
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

/**
 * WebSocket handler for workflow execution progress events.
 * Clients connect to /api/ws/workflow?token=<sa-token> to receive real-time updates.
 */
@Component
public class WorkflowWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // userId -> set of sessions
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastAccess = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket connection without token, closing");
            closeSession(session, new CloseStatus(401, "Unauthorized"));
            return;
        }

        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                log.warn("WebSocket token invalid, closing");
                closeSession(session, new CloseStatus(401, "Unauthorized"));
                return;
            }
            String userId = loginId.toString();
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
            sessionLastAccess.put(userId, System.currentTimeMillis());
            log.info("Workflow WebSocket connected: userId={}", userId);
        } catch (Exception e) {
            log.warn("Workflow WebSocket token validation failed: {}", e.getMessage());
            closeSession(session, new CloseStatus(401, "Unauthorized"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Clients don't need to send messages; connection is push-only
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdBySession(session);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) userSessions.remove(userId);
            }
            log.info("Workflow WebSocket disconnected: userId={}", userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Workflow WebSocket transport error: sessionId={}", session.getId(), exception);
        String userId = getUserIdBySession(session);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) sessions.remove(session);
        }
    }

    /**
     * Push a workflow event to all sessions for a given execution.
     * Events are scoped by executionId so clients can filter.
     */
    public void pushEvent(String userId, String eventType, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) return;

        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", eventType);
            event.put("timestamp", System.currentTimeMillis());
            event.putAll(payload);

            String json = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
            sessionLastAccess.put(userId, System.currentTimeMillis());
        } catch (IOException e) {
            log.error("Failed to push workflow event to userId={}", userId, e);
        }
    }

    /**
     * Convenience: push execution started event.
     */
    public void pushExecutionStarted(String userId, Long executionId, Long workflowId) {
        pushEvent(userId, "execution_started", Map.of(
                "executionId", executionId, "workflowId", workflowId));
    }

    /**
     * Convenience: push node completed event.
     */
    public void pushNodeCompleted(String userId, Long executionId, String nodeId,
                                   String nodeName, Map<String, Object> outputSummary) {
        pushEvent(userId, "node_completed", Map.of(
                "executionId", executionId,
                "nodeId", nodeId,
                "nodeName", nodeName,
                "outputSummary", outputSummary));
    }

    /**
     * Convenience: push node failed event.
     */
    public void pushNodeFailed(String userId, Long executionId, String nodeId,
                                String nodeName, String error) {
        pushEvent(userId, "node_failed", Map.of(
                "executionId", executionId,
                "nodeId", nodeId,
                "nodeName", nodeName,
                "error", error));
    }

    /**
     * Convenience: push execution completed event.
     */
    public void pushExecutionCompleted(String userId, Long executionId, String status,
                                        Map<String, Object> result) {
        pushEvent(userId, "execution_completed", Map.of(
                "executionId", executionId,
                "status", status,
                "result", result));
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
            if (entry.getValue().contains(session)) return entry.getKey();
        }
        return null;
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try { session.close(status); } catch (IOException ignored) {}
    }
}
