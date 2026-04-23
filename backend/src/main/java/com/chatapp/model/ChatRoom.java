package com.chatapp.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ChatRoom model representing a group chat room.
 * Demonstrates encapsulation by protecting the members set
 * and providing controlled access via add/remove methods.
 */
public class ChatRoom {

    private String roomId;
    private String name;
    private Set<String> members;
    private String createdBy;
    private String createdAt;

    // Default constructor for Gson
    public ChatRoom() {
        this.members = new HashSet<>();
    }

    /**
     * Creates a new chat room with the creator as the first member.
     * @param name      Room display name
     * @param createdBy Username of the room creator
     */
    public ChatRoom(String name, String createdBy) {
        this.roomId = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = Instant.now().toString();
        this.members = new HashSet<>();
        this.members.add(createdBy);  // Creator auto-joins
    }

    // --- Member Management ---

    /**
     * Adds a user to the room.
     * @param username The user to add
     * @return true if user was added, false if already a member
     */
    public boolean addMember(String username) {
        return members.add(username);
    }

    /**
     * Removes a user from the room.
     * @param username The user to remove
     * @return true if user was removed, false if not a member
     */
    public boolean removeMember(String username) {
        return members.remove(username);
    }

    /**
     * Checks if a user is a member of the room.
     * @param username The user to check
     * @return true if user is a member
     */
    public boolean isMember(String username) {
        return members.contains(username);
    }

    /**
     * Returns an unmodifiable view of the members set (encapsulation).
     */
    public Set<String> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    // --- Getters & Setters ---

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public void setMembers(Set<String> members) {
        this.members = members != null ? new HashSet<>(members) : new HashSet<>();
    }

    public int getMemberCount() {
        return members.size();
    }

    @Override
    public String toString() {
        return "ChatRoom{id='" + roomId + "', name='" + name +
               "', members=" + members.size() + "}";
    }
}
