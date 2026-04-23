package com.chatapp.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Message model representing a chat message.
 * Supports broadcast, private, group, and AI message types.
 * Demonstrates OOP encapsulation and builder-like factory methods.
 */
public class Message {

    /**
     * Enum for different message types — demonstrates use of enums for type safety.
     */
    public enum MessageType {
        BROADCAST,    // Sent to all connected users
        PRIVATE,      // Sent to a specific user (1-to-1)
        GROUP,        // Sent within a chat room
        AI,           // AI-generated response
        SYSTEM,       // System notifications (join, leave, etc.)
        AUTH          // Authentication messages (login/signup)
    }

    private String id;
    private String sender;
    private String content;
    private String timestamp;
    private MessageType type;
    private String recipient;    // For private messages
    private String roomId;       // For group messages

    // Default constructor for Gson
    public Message() {}

    /**
     * Full constructor for creating a message with all fields.
     */
    public Message(String id, String sender, String content, String timestamp,
                   MessageType type, String recipient, String roomId) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
        this.type = type;
        this.recipient = recipient;
        this.roomId = roomId;
    }

    // --- Static Factory Methods (Polymorphic creation) ---

    /**
     * Creates a broadcast message sent to all users.
     */
    public static Message broadcast(String sender, String content) {
        return new Message(
            UUID.randomUUID().toString(),
            sender, content, Instant.now().toString(),
            MessageType.BROADCAST, null, null
        );
    }

    /**
     * Creates a private message to a specific recipient.
     */
    public static Message privateMsg(String sender, String recipient, String content) {
        return new Message(
            UUID.randomUUID().toString(),
            sender, content, Instant.now().toString(),
            MessageType.PRIVATE, recipient, null
        );
    }

    /**
     * Creates a group message within a specific room.
     */
    public static Message groupMsg(String sender, String roomId, String content) {
        return new Message(
            UUID.randomUUID().toString(),
            sender, content, Instant.now().toString(),
            MessageType.GROUP, null, roomId
        );
    }

    /**
     * Creates a system notification message.
     */
    public static Message system(String content) {
        return new Message(
            UUID.randomUUID().toString(),
            "SYSTEM", content, Instant.now().toString(),
            MessageType.SYSTEM, null, null
        );
    }

    /**
     * Creates an AI response message.
     */
    public static Message aiResponse(String content, String originalSender) {
        return new Message(
            UUID.randomUUID().toString(),
            "AI Assistant", content, Instant.now().toString(),
            MessageType.AI, originalSender, null
        );
    }

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    @Override
    public String toString() {
        return "Message{sender='" + sender + "', type=" + type +
               ", content='" + (content != null && content.length() > 30
                    ? content.substring(0, 30) + "..." : content) + "'}";
    }
}
