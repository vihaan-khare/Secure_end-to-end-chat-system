package com.chatapp.server;

import com.chatapp.model.ChatRoom;
import com.chatapp.model.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.EncryptionService;
import com.chatapp.service.AIService;
import com.chatapp.service.AuthService;
import com.chatapp.service.ChatService;
import com.chatapp.util.LoggerService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main WebSocket Chat Server — the application entry point.
 *
 * Demonstrates:
 * - Inheritance: extends WebSocketServer
 * - Composition: aggregates services (Auth, Chat, AI, Encryption)
 * - Dependency Injection: services are wired together in the constructor
 * - Observer pattern: WebSocket callbacks drive the application flow
 *
 * Protocol: All messages are JSON objects with a "type" field:
 * - type: "signup" → { username, password }
 * - type: "login" → { username, password }
 * - type: "message" → { content, messageType, recipient?, roomId? }
 * - type: "createRoom"→ { roomName }
 * - type: "joinRoom" → { roomId }  ← room code required; room is invisible until joined
 * - type: "leaveRoom" → { roomId }
 * - type: "getHistory"→ { target?, roomId? }
 * - type: "getOnline" → (no extra fields)
 * - type: "getRooms" → (no extra fields)  ← returns ONLY rooms the caller is a member of
 * - type: "typing" → { recipient?, roomId? }
 */
public class ChatServer extends WebSocketServer {

    private final AuthService authService;
    private final ChatService chatService;
    private final AIService aiService;
    private final LoggerService logger = LoggerService.getInstance();
    private final Gson gson = new Gson();

    // Map WebSocket connections to their ClientHandlers
    private final ConcurrentHashMap<WebSocket, ClientHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Creates and wires all services together (Dependency Injection).
     */
    public ChatServer(int port) {
        super(new InetSocketAddress(port));

        // Initialize services with proper dependency chain
        EncryptionService encryptionService = new EncryptionService();
        UserRepository userRepository = new UserRepository();
        MessageRepository messageRepository = new MessageRepository(encryptionService);

        this.authService = new AuthService(userRepository);
        this.chatService = new ChatService(messageRepository);
        this.aiService = new AIService();

        // Wire the message delivery bridge
        chatService.setMessageDelivery(ClientHandler.createDeliveryBridge());

        logger.info("ChatServer initialized on port " + port);
    }

    // ==================== WEBSOCKET CALLBACKS ====================

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        ClientHandler handler = new ClientHandler(conn);
        handlers.put(conn, handler);
        logger.info("New connection from: " + conn.getRemoteSocketAddress());

        // Send welcome message
        sendJson(conn, Map.of(
                "type", "system",
                "content", "Welcome to SecureChat! Please login or signup.",
                "timestamp", java.time.Instant.now().toString()));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientHandler handler = handlers.remove(conn);
        if (handler != null) {
            String username = handler.getUsername();
            handler.disconnect();

            if (username != null) {
                // Notify others that this user went offline
                broadcastOnlineUsers();
                chatService.routeMessage(Message.system(username + " has left the chat"));
            }
        }
        logger.info("Connection closed: " + (reason != null ? reason : "unknown"));
    }

    @Override
    public void onMessage(WebSocket conn, String rawMessage) {
        try {
            JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";
            ClientHandler handler = handlers.get(conn);

            if (handler == null) {
                logger.error("No handler for connection");
                return;
            }

            switch (type) {
                case "signup" -> handleSignup(conn, handler, json);
                case "login" -> handleLogin(conn, handler, json);
                case "message" -> handleMessage(handler, json);
                case "createRoom" -> handleCreateRoom(handler, json);
                case "joinRoom" -> handleJoinRoom(handler, json);
                case "leaveRoom" -> handleLeaveRoom(handler, json);
                case "getHistory" -> handleGetHistory(handler, json);
                case "getOnline" -> handleGetOnline(handler);
                case "getRooms" -> handleGetRooms(handler);
                case "getAllUsers" -> handleGetAllUsers(handler);
                case "typing" -> handleTyping(handler, json);
                default -> sendJson(conn, Map.of(
                        "type", "error",
                        "content", "Unknown message type: " + type));
            }
        } catch (Exception e) {
            logger.error("Error processing message", e);
            sendJson(conn, Map.of("type", "error", "content", "Invalid message format"));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error", ex);
        if (conn != null) {
            sendJson(conn, Map.of("type", "error", "content", "Server error occurred"));
        }
    }

    @Override
    public void onStart() {
        logger.info("╔══════════════════════════════════════════╗");
        logger.info("║     SecureChat Server is RUNNING!        ║");
        logger.info("║     Port: " + getPort() + "                           ║");
        logger.info("║     AI: " + (aiService.isAvailable() ? "Enabled ✓" : "Disabled ✗") + "                       ║");
        logger.info("╚══════════════════════════════════════════╝");
    }

    // ==================== MESSAGE HANDLERS ====================

    /**
     * Handles user signup requests.
     */
    private void handleSignup(WebSocket conn, ClientHandler handler, JsonObject json) {
        String username = getJsonString(json, "username");
        String password = getJsonString(json, "password");

        String token = authService.signup(username, password);

        if (token != null) {
            handler.authenticate(username, token);
            sendJson(conn, Map.of(
                    "type", "auth",
                    "success", true,
                    "action", "signup",
                    "username", username,
                    "token", token,
                    "content", "Account created successfully! Welcome, " + username + "!"));

            // Notify everyone about new user
            broadcastOnlineUsers();
            chatService.routeMessage(Message.system(username + " has joined the chat! 🎉"));
        } else {
            sendJson(conn, Map.of(
                    "type", "auth",
                    "success", false,
                    "action", "signup",
                    "content", "Signup failed. Username may be taken or invalid (3-20 chars, password 4+ chars)."));
        }
    }

    /**
     * Handles user login requests.
     */
    private void handleLogin(WebSocket conn, ClientHandler handler, JsonObject json) {
        String username = getJsonString(json, "username");
        String password = getJsonString(json, "password");

        String token = authService.login(username, password);

        if (token != null) {
            handler.authenticate(username, token);
            sendJson(conn, Map.of(
                    "type", "auth",
                    "success", true,
                    "action", "login",
                    "username", username,
                    "token", token,
                    "content", "Welcome back, " + username + "!"));

            // Send chat history
            sendChatHistory(handler);

            // Notify everyone
            broadcastOnlineUsers();
            chatService.routeMessage(Message.system(username + " is now online"));
        } else {
            sendJson(conn, Map.of(
                    "type", "auth",
                    "success", false,
                    "action", "login",
                    "content", "Login failed. Check your username and password."));
        }
    }

    /**
     * Handles incoming chat messages (broadcast, private, group, AI).
     */
    private void handleMessage(ClientHandler handler, JsonObject json) {
        if (!handler.isAuthenticated()) {
            handler.sendMessage(gson.toJson(Map.of("type", "error", "content", "Please login first")));
            return;
        }

        String content = getJsonString(json, "content");
        String messageType = getJsonString(json, "messageType");
        String recipient = getJsonString(json, "recipient");
        String roomId = getJsonString(json, "roomId");

        if (content == null || content.isBlank())
            return;

        // Check for AI trigger — accepts "@ai", "@ai ", or "@ai <question>"
        String lower = content.toLowerCase().trim();
        if (lower.equals("@ai") || lower.startsWith("@ai ")) {
            String question = content.substring(3).trim(); // strip "@ai"
            if (question.isEmpty()) {
                // User typed @ai with no question — give a helpful nudge
                handler.sendMessage(gson.toJson(Map.of(
                        "type", "message",
                        "id", java.util.UUID.randomUUID().toString(),
                        "sender", "AI Assistant",
                        "content", "🤖 Hi! Ask me anything — e.g. '@ai what is polymorphism?'",
                        "timestamp", java.time.Instant.now().toString(),
                        "messageType", "AI")));
                return;
            }
            handleAIMessage(handler, question);
            return;
        }

        // Route based on message type
        Message message;
        if ("PRIVATE".equalsIgnoreCase(messageType) && recipient != null) {
            message = Message.privateMsg(handler.getUsername(), recipient, content);
        } else if ("GROUP".equalsIgnoreCase(messageType) && roomId != null) {
            message = Message.groupMsg(handler.getUsername(), roomId, content);
        } else {
            message = Message.broadcast(handler.getUsername(), content);
        }

        chatService.routeMessage(message);
    }

    /**
     * Handles AI chat messages.
     */
    private void handleAIMessage(ClientHandler handler, String question) {
        // First, broadcast the user's question
        Message userMsg = Message.broadcast(handler.getUsername(), "@ai " + question);
        chatService.routeMessage(userMsg);

        // Get AI response in a separate thread to avoid blocking
        new Thread(() -> {
            String aiResponse = aiService.getAIResponse(question);
            Message aiMsg = Message.aiResponse(aiResponse, handler.getUsername());
            chatService.routeMessage(aiMsg);
        }, "ai-response-thread").start();
    }

    /**
     * Handles room creation.
     */
    private void handleCreateRoom(ClientHandler handler, JsonObject json) {
        if (!handler.isAuthenticated())
            return;

        String roomName = getJsonString(json, "roomName");
        if (roomName == null || roomName.isBlank()) {
            handler.sendMessage(gson.toJson(Map.of("type", "error", "content", "Room name required")));
            return;
        }

        ChatRoom room = chatService.createRoom(roomName, handler.getUsername());

        // Notify the creator with the room details (including the join code)
        handler.sendMessage(gson.toJson(Map.of(
                "type", "roomCreated",
                "roomId", room.getRoomId(),
                "roomName", room.getName(),
                "createdBy", room.getCreatedBy(),
                "content", "Room '" + room.getName() + "' created!")));

        // Only update the creator's sidebar — rooms are invite-only, nobody else learns
        // this room exists until they enter the correct room code.
        sendRoomsToUser(handler);
    }

    /**
     * Handles joining a room.
     */
    private void handleJoinRoom(ClientHandler handler, JsonObject json) {
        if (!handler.isAuthenticated())
            return;

        String roomId = getJsonString(json, "roomId");

        // Rooms are private — a null/unknown roomId must be rejected silently
        // so that the user cannot probe for room existence.
        if (roomId == null || roomId.isBlank()) {
            handler.sendMessage(gson.toJson(Map.of(
                    "type", "error", "content", "Invalid room code")));
            return;
        }

        boolean joined = chatService.joinRoom(roomId, handler.getUsername());

        if (joined) {
            ChatRoom room = chatService.getRoom(roomId);
            handler.sendMessage(gson.toJson(Map.of(
                    "type", "roomJoined",
                    "roomId", roomId,
                    "roomName", room != null ? room.getName() : "",
                    "content", "Joined room successfully!")));

            // Notify existing room members that someone new joined
            Message joinMsg = Message.groupMsg("SYSTEM", roomId,
                    handler.getUsername() + " joined the room");
            chatService.routeMessage(joinMsg);

            // Only refresh this user's sidebar — other users still cannot see the room
            sendRoomsToUser(handler);
        } else {
            // Use a generic error so room existence is not revealed
            handler.sendMessage(gson.toJson(Map.of(
                    "type", "error", "content", "Invalid room code or could not join")));
        }
    }

    /**
     * Handles leaving a room.
     */
    private void handleLeaveRoom(ClientHandler handler, JsonObject json) {
        if (!handler.isAuthenticated())
            return;

        String roomId = getJsonString(json, "roomId");
        boolean left = chatService.leaveRoom(roomId, handler.getUsername());

        if (left) {
            handler.sendMessage(gson.toJson(Map.of(
                    "type", "roomLeft", "roomId", roomId,
                    "content", "Left the room")));

            Message leaveMsg = Message.groupMsg("SYSTEM", roomId,
                    handler.getUsername() + " left the room");
            chatService.routeMessage(leaveMsg);

            // Only refresh this user's sidebar
            sendRoomsToUser(handler);
        }
    }

    /**
     * Sends chat history to a newly connected user.
     */
    private void handleGetHistory(ClientHandler handler, JsonObject json) {
        if (!handler.isAuthenticated())
            return;

        String target = getJsonString(json, "target");
        String roomId = getJsonString(json, "roomId");

        List<Message> history;
        if (roomId != null) {
            history = chatService.getRoomHistory(roomId);
        } else if (target != null) {
            history = chatService.getPrivateHistory(handler.getUsername(), target);
        } else {
            history = chatService.getRecentBroadcasts();
        }

        sendHistory(handler, history);
    }

    /**
     * Sends the initial chat history on login.
     */
    private void sendChatHistory(ClientHandler handler) {
        List<Message> history = chatService.getRecentBroadcasts();
        sendHistory(handler, history);
    }

    private void sendHistory(ClientHandler handler, List<Message> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message m : history) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("id", m.getId());
            msg.put("sender", m.getSender());
            msg.put("content", m.getContent());
            msg.put("timestamp", m.getTimestamp());
            msg.put("messageType", m.getType().name());
            if (m.getRecipient() != null)
                msg.put("recipient", m.getRecipient());
            if (m.getRoomId() != null)
                msg.put("roomId", m.getRoomId());
            messages.add(msg);
        }

        handler.sendMessage(gson.toJson(Map.of(
                "type", "history",
                "messages", messages)));
    }

    /**
     * Sends online users list to the requesting client.
     */
    private void handleGetOnline(ClientHandler handler) {
        handler.sendMessage(gson.toJson(Map.of(
                "type", "onlineUsers",
                "users", ClientHandler.getOnlineUsers())));
    }

    /**
     * Sends ALL registered users (online + offline) to the requesting client.
     * The frontend uses this to power the full user search feature.
     */
    private void handleGetAllUsers(ClientHandler handler) {
        if (!handler.isAuthenticated()) return;

        java.util.List<String> allUsernames = authService.getAllUsernames();
        java.util.Set<String> onlineSet = new java.util.HashSet<>(ClientHandler.getOnlineUsers());

        java.util.List<java.util.Map<String, Object>> userList = new java.util.ArrayList<>();
        for (String username : allUsernames) {
            userList.add(Map.of(
                    "username", username,
                    "online", onlineSet.contains(username)));
        }

        handler.sendMessage(gson.toJson(Map.of(
                "type", "allUsers",
                "users", userList)));
    }

    /**
     * Sends room list to the requesting client.
     */
    private void handleGetRooms(ClientHandler handler) {
        if (!handler.isAuthenticated()) return;
        // Delegate to the filtered helper so the response only contains
        // rooms this specific user is already a member of.
        sendRoomsToUser(handler);
    }

    /**
     * Handles typing indicator forwarding.
     */
    private void handleTyping(ClientHandler handler, JsonObject json) {
        if (!handler.isAuthenticated())
            return;

        String recipient = getJsonString(json, "recipient");
        String roomId = getJsonString(json, "roomId");

        Map<String, Object> typingEvent = Map.of(
                "type", "typing",
                "username", handler.getUsername());

        if (recipient != null) {
            ClientHandler.deliverToUser(recipient, gson.toJson(typingEvent));
        } else if (roomId != null) {
            ChatRoom room = chatService.getRoom(roomId);
            if (room != null) {
                for (String member : room.getMembers()) {
                    if (!member.equals(handler.getUsername())) {
                        ClientHandler.deliverToUser(member, gson.toJson(typingEvent));
                    }
                }
            }
        } else {
            // Broadcast typing to all
            for (String user : ClientHandler.getOnlineUsers()) {
                if (!user.equals(handler.getUsername())) {
                    ClientHandler.deliverToUser(user, gson.toJson(typingEvent));
                }
            }
        }
    }

    // ==================== BROADCAST HELPERS ====================

    /**
     * Broadcasts the online users list to all connected clients.
     */
    private void broadcastOnlineUsers() {
        String json = gson.toJson(Map.of(
                "type", "onlineUsers",
                "users", ClientHandler.getOnlineUsers()));
        ClientHandler.deliverToAll(json);
    }

    /**
     * Sends only the rooms this specific user is a member of to their client.
     * Rooms are invite-only — users must know the room code to join; they
     * cannot discover rooms they are not already in.
     */
    private void sendRoomsToUser(ClientHandler handler) {
        String username = handler.getUsername();
        Collection<ChatRoom> allRooms = chatService.getAllRooms();
        List<Map<String, Object>> roomList = new ArrayList<>();

        for (ChatRoom room : allRooms) {
            // Only include rooms the requesting user belongs to
            if (room.isMember(username)) {
                roomList.add(Map.of(
                        "roomId", room.getRoomId(),
                        "name", room.getName(),
                        "createdBy", room.getCreatedBy(),
                        "memberCount", room.getMemberCount(),
                        "members", room.getMembers()));
            }
        }

        handler.sendMessage(gson.toJson(Map.of(
                "type", "roomList",
                "rooms", roomList)));
    }

    // ==================== UTILITY ====================

    /**
     * Sends a JSON map to a WebSocket connection.
     */
    private void sendJson(WebSocket conn, Map<String, Object> data) {
        if (conn != null && conn.isOpen()) {
            conn.send(gson.toJson(data));
        }
    }

    /**
     * Safely extracts a string from a JsonObject.
     */
    private String getJsonString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull()
                ? json.get(key).getAsString()
                : null;
    }

    // ==================== MAIN ====================

    /**
     * Application entry point — starts the WebSocket server.
     */
    public static void main(String[] args) {
        LoggerService logger = LoggerService.getInstance();

        // Load port from .env or default to 8887
        int port;
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            port = Integer.parseInt(dotenv.get("SERVER_PORT", "8887"));
        } catch (Exception e) {
            port = 8887;
        }

        ChatServer server = new ChatServer(port);
        server.start();

        logger.info("Server started. Press Ctrl+C to stop.");

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            try {
                server.stop(1000);
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
            logger.close();
        }));
    }
}
