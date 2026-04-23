package com.chatapp.service;

import com.chatapp.model.ChatRoom;
import com.chatapp.model.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.util.LoggerService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat Service handling message routing, room management, and chat history.
 * Demonstrates:
 * - Strategy pattern for message routing based on type
 * - Thread-safe room management with ConcurrentHashMap
 * - Clean separation of messaging logic from networking
 */
public class ChatService {

    /**
     * Interface for receiving routed messages (Observer/Callback pattern).
     * The ChatServer will implement this to send messages to WebSocket clients.
     */
    public interface MessageDelivery {
        void deliverToUser(String username, String jsonMessage);

        void deliverToAll(String jsonMessage);

        void deliverToUsers(Set<String> usernames, String jsonMessage);
    }

    private final MessageRepository messageRepository;
    private final LoggerService logger = LoggerService.getInstance();

    // Active chat rooms: roomId -> ChatRoom
    private final ConcurrentHashMap<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();

    // Callback for message delivery (set by ChatServer)
    private MessageDelivery messageDelivery;

    public ChatService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Sets the message delivery callback (Dependency Injection).
     */
    public void setMessageDelivery(MessageDelivery delivery) {
        this.messageDelivery = delivery;
    }

    // ==================== MESSAGE ROUTING ====================

    /**
     * Routes a message based on its type — polymorphic dispatch.
     */
    public void routeMessage(Message message) {
        // Persist message
        messageRepository.save(message);

        // Route based on type
        switch (message.getType()) {
            case BROADCAST -> handleBroadcast(message);
            case PRIVATE -> handlePrivateMessage(message);
            case GROUP -> handleGroupMessage(message);
            case SYSTEM -> handleBroadcast(message);
            default -> logger.warn("Unknown message type: " + message.getType());
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     */
    private void handleBroadcast(Message message) {
        if (messageDelivery != null) {
            messageDelivery.deliverToAll(messageToJson(message));
        }
        logger.info("Broadcast from " + message.getSender() + ": " + message.getContent());
    }

    /**
     * Sends a private message to a specific user.
     */
    private void handlePrivateMessage(Message message) {
        if (messageDelivery != null) {
            String json = messageToJson(message);
            // Deliver to recipient
            messageDelivery.deliverToUser(message.getRecipient(), json);
            // Also deliver back to sender (so they see their own message)
            messageDelivery.deliverToUser(message.getSender(), json);
        }
        logger.info("Private message: " + message.getSender() + " -> " + message.getRecipient());
    }

    /**
     * Sends a message to all members of a group/room.
     */
    private void handleGroupMessage(Message message) {
        ChatRoom room = chatRooms.get(message.getRoomId());
        if (room == null) {
            logger.warn("Group message to unknown room: " + message.getRoomId());
            return;
        }
        if (messageDelivery != null) {
            messageDelivery.deliverToUsers(room.getMembers(), messageToJson(message));
        }
        logger.info("Group message in " + room.getName() + " from " + message.getSender());
    }

    // ==================== ROOM MANAGEMENT ====================

    /**
     * Creates a new chat room.
     */
    public ChatRoom createRoom(String name, String createdBy) {
        ChatRoom room = new ChatRoom(name, createdBy);
        chatRooms.put(room.getRoomId(), room);
        logger.info("Room created: " + room.getName() + " by " + createdBy);
        return room;
    }

    /**
     * Joins a user to an existing room.
     */
    public boolean joinRoom(String roomId, String username) {
        ChatRoom room = chatRooms.get(roomId);
        if (room == null) {
            logger.warn("Join failed: room not found - " + roomId);
            return false;
        }
        boolean joined = room.addMember(username);
        if (joined) {
            logger.info(username + " joined room: " + room.getName());
        }
        return joined;
    }

    /**
     * Removes a user from a room.
     */
    public boolean leaveRoom(String roomId, String username) {
        ChatRoom room = chatRooms.get(roomId);
        if (room == null)
            return false;
        boolean left = room.removeMember(username);
        if (left) {
            logger.info(username + " left room: " + room.getName());
        }
        return left;
    }

    /**
     * Gets all available rooms.
     */
    public Collection<ChatRoom> getAllRooms() {
        return chatRooms.values();
    }

    /**
     * Gets a specific room by ID.
     */
    public ChatRoom getRoom(String roomId) {
        return chatRooms.get(roomId);
    }

    // ==================== CHAT HISTORY ====================

    /**
     * Gets recent broadcast messages for a new user joining.
     */
    public List<Message> getRecentBroadcasts() {
        return messageRepository.findRecentBroadcasts(50);
    }

    /**
     * Gets private message history between two users.
     */
    public List<Message> getPrivateHistory(String user1, String user2) {
        return messageRepository.findPrivateMessages(user1, user2);
    }

    /**
     * Gets message history for a room.
     */
    public List<Message> getRoomHistory(String roomId) {
        return messageRepository.findByRoom(roomId);
    }

    // ==================== HELPERS ====================

    /**
     * Converts a Message to JSON string for WebSocket delivery.
     */
    private String messageToJson(Message message) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "message");
        json.put("id", message.getId());
        json.put("sender", message.getSender());
        json.put("content", message.getContent());
        json.put("timestamp", message.getTimestamp());
        json.put("messageType", message.getType().name());
        if (message.getRecipient() != null)
            json.put("recipient", message.getRecipient());
        if (message.getRoomId() != null)
            json.put("roomId", message.getRoomId());
        return gson.toJson(json);
    }
}
