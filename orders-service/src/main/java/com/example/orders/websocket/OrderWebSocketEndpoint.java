package com.example.orders.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.util.logging.Level;
import java.util.logging.Logger;

@ServerEndpoint("/ws/orders")
public class OrderWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(OrderWebSocketEndpoint.class.getName());

    @OnOpen
    public void onOpen(Session session) {
        OrderSessionRegistry.register(session);
        LOG.info(() -> "WebSocket client connected: " + session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        OrderSessionRegistry.unregister(session);
        LOG.info(() -> "WebSocket client disconnected: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOG.log(Level.WARNING, "WebSocket error for session " + session.getId(), throwable);
        OrderSessionRegistry.unregister(session);
    }
}
