package io.llmplatform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 本机双向通道，推送状态与中间事件。 */
@Component
public class PlatformWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public PlatformWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        publish("status", "connected", Map.of());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void publish(String type, String requestId, Map<String, Object> payload) {
        String json;
        try {
            json =
                    objectMapper.writeValueAsString(
                            Map.of("type", type, "requestId", requestId, "payload", payload));
        } catch (IOException ex) {
            log.warn("无法序列化 WebSocket 事件");
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            synchronized (session) {
                try {
                    session.sendMessage(message);
                } catch (IOException ex) {
                    log.debug("WebSocket 发送失败");
                }
            }
        }
    }
}
