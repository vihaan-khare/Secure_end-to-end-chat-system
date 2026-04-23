package com.chatapp.service;

import com.chatapp.model.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.util.LoggerService;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication Service handling user signup, login, and session management.
 * Demonstrates:
 * - BCrypt password hashing (never stores plaintext passwords)
 * - Session token management using ConcurrentHashMap (thread-safe)
 * - Separation of authentication logic from networking code
 */
public class AuthService {

    private final UserRepository userRepository;
    private final LoggerService logger = LoggerService.getInstance();

    // Active sessions: token -> username (thread-safe)
    private final ConcurrentHashMap<String, String> activeSessions = new ConcurrentHashMap<>();

    // Reverse map: username -> token (for quick lookup)
    private final ConcurrentHashMap<String, String> userTokens = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user with a hashed password.
     *
     * @param username The desired username
     * @param password The plaintext password (will be hashed)
     * @return A session token on success, or null on failure
     */
    public String signup(String username, String password) {
        // Validate input
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            logger.warn("Signup failed: empty username or password");
            return null;
        }

        if (username.length() < 3 || username.length() > 20) {
            logger.warn("Signup failed: username length must be 3-20 characters");
            return null;
        }

        if (password.length() < 4) {
            logger.warn("Signup failed: password too short");
            return null;
        }

        // Check if username already exists
        if (userRepository.existsByUsername(username)) {
            logger.warn("Signup failed: username already taken - " + username);
            return null;
        }

        // Hash password with BCrypt (auto-generates salt)
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        // Create and save user
        User user = new User(username, passwordHash, Instant.now().toString());
        boolean saved = userRepository.save(user);

        if (!saved) {
            logger.error("Signup failed: could not save user - " + username);
            return null;
        }

        // Generate session token
        String token = generateToken();
        activeSessions.put(token, username);
        userTokens.put(username, token);

        logger.info("User signed up successfully: " + username);
        return token;
    }

    /**
     * Authenticates a user with their credentials.
     *
     * @param username The username
     * @param password The plaintext password to verify
     * @return A session token on success, or null on failure
     */
    public String login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        // Look up user
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            logger.warn("Login failed: user not found - " + username);
            return null;
        }

        User user = optionalUser.get();

        // Verify password with BCrypt
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            logger.warn("Login failed: wrong password - " + username);
            return null;
        }

        // Invalidate old session if exists
        String oldToken = userTokens.get(username);
        if (oldToken != null) {
            activeSessions.remove(oldToken);
        }

        // Create new session
        String token = generateToken();
        activeSessions.put(token, username);
        userTokens.put(username, token);

        logger.info("User logged in: " + username);
        return token;
    }

    /**
     * Validates a session token and returns the associated username.
     */
    public String validateToken(String token) {
        return activeSessions.get(token);
    }

    /**
     * Logs out a user by invalidating their session token.
     */
    public void logout(String token) {
        String username = activeSessions.remove(token);
        if (username != null) {
            userTokens.remove(username);
            logger.info("User logged out: " + username);
        }
    }

    /**
     * Gets all active sessions (for online users list).
     */
    public Map<String, String> getActiveSessions() {
        return Map.copyOf(activeSessions);
    }

    /**
     * Checks if a user is currently logged in.
     */
    public boolean isOnline(String username) {
        return userTokens.containsKey(username);
    }

    /**
     * Returns all registered usernames from the database.
     * Used by the frontend to power the "search all users" feature,
     * including offline users.
     */
    public java.util.List<String> getAllUsernames() {
        return userRepository.findAll().stream()
                .map(User::getUsername)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Generates a unique session token.
     */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
