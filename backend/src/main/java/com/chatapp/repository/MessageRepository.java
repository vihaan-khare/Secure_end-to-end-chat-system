package com.chatapp.repository;

import com.chatapp.model.Message;
import com.chatapp.security.EncryptionService;
import com.chatapp.util.LoggerService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for Message persistence using JSON file storage.
 * Messages are encrypted before storage and decrypted on retrieval.
 * Demonstrates the Repository pattern with encryption integration.
 */
public class MessageRepository {

    private final String filePath;
    private final Gson gson;
    private final EncryptionService encryptionService;
    private final LoggerService logger = LoggerService.getInstance();
    private final Type messageListType = new TypeToken<List<Message>>() {
    }.getType();

    public MessageRepository(EncryptionService encryptionService) {
        this.filePath = resolveDataPath("messages.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.encryptionService = encryptionService;
        ensureFileExists();
        logger.info("MessageRepository initialized at: " + filePath);
    }

    /**
     * Saves a message with encrypted content.
     */
    public synchronized void save(Message message) {
        try {
            List<Message> messages = readAllRaw();

            // Create a copy with encrypted content for storage
            Message encrypted = new Message(
                    message.getId(), message.getSender(),
                    encryptionService.encrypt(message.getContent()),
                    message.getTimestamp(), message.getType(),
                    message.getRecipient(), message.getRoomId());
            messages.add(encrypted);

            writeAll(messages);
            logger.info("Message saved (encrypted): " + message.getId());
        } catch (Exception e) {
            logger.error("Failed to save message", e);
        }
    }

    /**
     * Retrieves all messages, decrypting content on the fly.
     */
    public synchronized List<Message> findAll() {
        return readAllRaw().stream().map(this::decryptMessage).collect(Collectors.toList());
    }

    /**
     * Retrieves messages for a specific room.
     */
    public synchronized List<Message> findByRoom(String roomId) {
        return readAllRaw().stream()
                .filter(m -> roomId.equals(m.getRoomId()))
                .map(this::decryptMessage)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the last N broadcast messages (for chat history).
     */
    public synchronized List<Message> findRecentBroadcasts(int limit) {
        List<Message> broadcasts = readAllRaw().stream()
                .filter(m -> m.getType() == Message.MessageType.BROADCAST ||
                        m.getType() == Message.MessageType.SYSTEM)
                .map(this::decryptMessage)
                .collect(Collectors.toList());

        int start = Math.max(0, broadcasts.size() - limit);
        return broadcasts.subList(start, broadcasts.size());
    }

    /**
     * Retrieves private messages between two users.
     */
    public synchronized List<Message> findPrivateMessages(String user1, String user2) {
        return readAllRaw().stream()
                .filter(m -> m.getType() == Message.MessageType.PRIVATE)
                .filter(m -> (user1.equals(m.getSender()) && user2.equals(m.getRecipient())) ||
                        (user2.equals(m.getSender()) && user1.equals(m.getRecipient())))
                .map(this::decryptMessage)
                .collect(Collectors.toList());
    }

    // --- Internal ---

    private Message decryptMessage(Message m) {
        try {
            return new Message(
                    m.getId(), m.getSender(),
                    encryptionService.decrypt(m.getContent()),
                    m.getTimestamp(), m.getType(),
                    m.getRecipient(), m.getRoomId());
        } catch (Exception e) {
            return m; // Return as-is if decryption fails
        }
    }

    private List<Message> readAllRaw() {
        try {
            String content = Files.readString(Path.of(filePath));
            if (content.isBlank())
                return new ArrayList<>();
            List<Message> messages = gson.fromJson(content, messageListType);
            return messages != null ? messages : new ArrayList<>();
        } catch (IOException e) {
            logger.error("Failed to read messages file", e);
            return new ArrayList<>();
        }
    }

    private void writeAll(List<Message> messages) {
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(messages, writer);
        } catch (IOException e) {
            logger.error("Failed to write messages file", e);
        }
    }

    private void ensureFileExists() {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "[]");
            }
        } catch (IOException e) {
            logger.error("Failed to create messages file", e);
        }
    }

    private String resolveDataPath(String filename) {
        String resourcePath = "src/main/resources/data/" + filename;
        if (Files.exists(Path.of(resourcePath))) {
            return resourcePath;
        }
        return "data/" + filename;
    }
}
