package com.chatapp.server;

import com.chatapp.model.Message;
import com.chatapp.service.ChatService;
import com.chatapp.util.LoggerService;
import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientHandler manages the association between a WebSocket connection and a
 * user.
 * Each connected client gets a ClientHandler instance.
 *
 * Demonstrates:
 * - Encapsulation: connection state is private
 * - Static registry: tracks all active handlers for message delivery
 */
public class ClientHandler {

    // Static registry of all active handlers: username -> ClientHandler
    private static final ConcurrentHashMap<String, ClientHandler> activeHandlers = new ConcurrentHashMap<>();

    private final WebSocket connection;
    private final LoggerService logger = LoggerService.getInstance();

    private String username;
    private String sessionToken;
    private boolean authenticated = false;

    /**
     * Creates a new ClientHandler for a WebSocket connection.
     * 
     * @param connection The WebSocket connection
     */
    public ClientHandler(WebSocket connection) {
        this.connection = connection;
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Authenticates and registers this handler with a username.
     */
    public void authenticate(String username, String token) {
        this.username = username;
        this.sessionToken = token;
        this.authenticated = true;

        // Remove any existing handler for this username (reconnection)
        ClientHandler old = activeHandlers.get(username);
        if (old != null && old != this) {
            old.sendMessage("{\"type\":\"system\",\"content\":\"Logged in from another location\"}");
            old.connection.close();
        }

        activeHandlers.put(username, this);
        logger.info("ClientHandler registered: " + username);
    }

    /**
     * Deregisters this handler on disconnect.
     */
    public void disconnect() {
        if (username != null) {
            activeHandlers.remove(username, this);
            logger.info("ClientHandler deregistered: " + username);
        }
    }

    // ==================== MESSAGE DELIVERY ====================

    /**
     * Sends a JSON message to this client's WebSocket connection.
     */
    public void sendMessage(String jsonMessage) {
        if (connection != null && connection.isOpen()) {
            connection.send(jsonMessage);
        }
    }

    // ==================== STATIC DELIVERY METHODS ====================

    /**
     * Delivers a message to a specific user by username.
     */
    public static void deliverToUser(String username, String jsonMessage) {
        ClientHandler handler = activeHandlers.get(username);
        if (handler != null) {
            handler.sendMessage(jsonMessage);
        }
    }

    /**
     * Delivers a message to all connected and authenticated clients.
     */
    public static void deliverToAll(String jsonMessage) {
        for (ClientHandler handler : activeHandlers.values()) {
            handler.sendMessage(jsonMessage);
        }
    }

    /**
     * Delivers a message to a set of specific usernames.
     */
    public static void deliverToUsers(Set<String> usernames, String jsonMessage) {
        for (String username : usernames) {
            deliverToUser(username, jsonMessage);
        }
    }

    /**
     * Returns a set of all currently online usernames.
     */
    public static Set<String> getOnlineUsers() {
        return Set.copyOf(activeHandlers.keySet());
    }

    /**
     * Checks if a user is currently connected.
     */
    public static boolean isOnline(String username) {
        return activeHandlers.containsKey(username);
    }

    /**
     * Returns how many clients are connected.
     */
    public static int getOnlineCount() {
        return activeHandlers.size();
    }

    /**
     * Creates a ChatService.MessageDelivery implementation using the static
     * methods.
     * This bridges the ChatService with the WebSocket delivery system.
     */
    public static ChatService.MessageDelivery createDeliveryBridge() {
        return new ChatService.MessageDelivery() {
            @Override
            public void deliverToUser(String username, String jsonMessage) {
                ClientHandler.deliverToUser(username, jsonMessage);
            }

            @Override
            public void deliverToAll(String jsonMessage) {
                ClientHandler.deliverToAll(jsonMessage);
            }

            @Override
            public void deliverToUsers(Set<String> usernames, String jsonMessage) {
                ClientHandler.deliverToUsers(usernames, jsonMessage);
            }
        };
    }

    // ==================== GETTERS ====================

    public String getUsername() {
        return username;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public WebSocket getConnection() {
        return connection;
    }
}
