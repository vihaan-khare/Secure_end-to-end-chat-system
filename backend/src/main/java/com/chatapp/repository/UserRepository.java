package com.chatapp.repository;

import com.chatapp.model.User;
import com.chatapp.util.LoggerService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository for User CRUD operations using JSON file storage.
 * Demonstrates the Repository pattern — data access is separated from business
 * logic.
 * Thread-safe with synchronized read/write operations.
 */
public class UserRepository {

    private final String filePath;
    private final Gson gson;
    private final LoggerService logger = LoggerService.getInstance();
    private final Type userListType = new TypeToken<List<User>>() {
    }.getType();

    /**
     * Initializes the repository, creating the data file if it doesn't exist.
     */
    public UserRepository() {
        // Look for data directory relative to working directory
        this.filePath = resolveDataPath("users.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        ensureFileExists();
        logger.info("UserRepository initialized at: " + filePath);
    }

    /**
     * Finds a user by username.
     * 
     * @param username The username to search for
     * @return Optional containing the User if found
     */
    public synchronized Optional<User> findByUsername(String username) {
        List<User> users = readAll();
        return users.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    /**
     * Checks if a username already exists.
     */
    public synchronized boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    /**
     * Saves a new user to the JSON file.
     * 
     * @param user The user to save
     * @return true if saved successfully
     */
    public synchronized boolean save(User user) {
        try {
            List<User> users = readAll();
            // Check for duplicate
            if (users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(user.getUsername()))) {
                logger.warn("User already exists: " + user.getUsername());
                return false;
            }
            users.add(user);
            writeAll(users);
            logger.info("User saved: " + user.getUsername());
            return true;
        } catch (Exception e) {
            logger.error("Failed to save user", e);
            return false;
        }
    }

    /**
     * Returns all registered users.
     */
    public synchronized List<User> findAll() {
        return readAll();
    }

    // --- Internal File I/O ---

    private List<User> readAll() {
        try {
            String content = Files.readString(Path.of(filePath));
            if (content.isBlank())
                return new ArrayList<>();
            List<User> users = gson.fromJson(content, userListType);
            return users != null ? users : new ArrayList<>();
        } catch (IOException e) {
            logger.error("Failed to read users file", e);
            return new ArrayList<>();
        }
    }

    private void writeAll(List<User> users) {
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(users, writer);
        } catch (IOException e) {
            logger.error("Failed to write users file", e);
        }
    }

    private void ensureFileExists() {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "[]");
                logger.info("Created users data file: " + filePath);
            }
        } catch (IOException e) {
            logger.error("Failed to create users file", e);
        }
    }

    /**
     * Resolves the path to a data file, checking multiple possible locations.
     */
    private String resolveDataPath(String filename) {
        // Check resources/data first
        String resourcePath = "src/main/resources/data/" + filename;
        if (Files.exists(Path.of(resourcePath))) {
            return resourcePath;
        }
        // Fallback: create in data/ directory
        return "data/" + filename;
    }
}
