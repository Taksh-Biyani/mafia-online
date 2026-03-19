package com.mafia.game.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Manages WebSocket connections grouped by room.
 * Sends a lightweight "refresh" message to every connected client in a room
 * whenever game state changes, letting clients pull the new state via REST.
 */
@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final Map<UUID, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID roomId = roomIdFrom(session);
        if (roomId != null) {
            sessionsByRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID roomId = roomIdFrom(session);
        if (roomId == null) return;
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) sessionsByRoom.remove(roomId);
        }
    }

    /** Sends "refresh" to every open WebSocket session in the given room. */
    public void broadcast(UUID roomId) {
        Set<WebSocketSession> sessions = sessionsByRoom.getOrDefault(roomId, Collections.emptySet());
        TextMessage msg = new TextMessage("refresh");
        sessions.removeIf(s -> !s.isOpen());
        for (WebSocketSession s : sessions) {
            try {
                s.sendMessage(msg);
            } catch (IOException e) {
                sessions.remove(s);
            }
        }
    }

    private UUID roomIdFrom(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String[] parts = uri.getPath().split("/");
        if (parts.length == 0) return null;
        try {
            return UUID.fromString(parts[parts.length - 1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
