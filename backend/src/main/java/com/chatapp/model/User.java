package com.chatapp.model;

/**
 * User model representing a registered chat user.
 * Demonstrates encapsulation with private fields and public getters/setters.
 */
public class User {

    private String username;
    private String passwordHash;
    private String createdAt;

    // Default constructor for Gson deserialization
    public User() {}

    /**
     * Parameterized constructor for creating a new user.
     * @param username   Unique username
     * @param passwordHash BCrypt-hashed password
     * @param createdAt  ISO timestamp of account creation
     */
    public User(String username, String passwordHash, String createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    // --- Getters & Setters (Encapsulation) ---

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', createdAt='" + createdAt + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return username != null && username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}
