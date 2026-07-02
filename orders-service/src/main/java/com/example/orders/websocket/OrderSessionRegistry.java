package com.example.orders.websocket;

import jakarta.websocket.Session;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OrderSessionRegistry {

    private static final Logger LOG = Logger.getLogger(OrderSessionRegistry.class.getName());
    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    private OrderSessionRegistry() {
    }

    public static void register(Session session) {
        SESSIONS.add(session);
    }

    public static void unregister(Session session) {
        SESSIONS.remove(session);
    }

    public static void broadcast(String message) {
        for (Session session : SESSIONS) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to send WebSocket message", e);
                }
            }
        }
    }
}
