package com.chatapp.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Singleton Logger Service for centralized application logging.
 * Demonstrates the Singleton design pattern — only one instance exists.
 * Logs to both console and an optional log file.
 */
public class LoggerService {

    // Singleton instance (eager initialization)
    private static final LoggerService INSTANCE = new LoggerService();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PrintWriter fileWriter;
    private boolean fileLoggingEnabled = false;

    /**
     * Private constructor prevents external instantiation (Singleton pattern).
     */
    private LoggerService() {
        try {
            fileWriter = new PrintWriter(new FileWriter("chat-server.log", true), true);
            fileLoggingEnabled = true;
        } catch (IOException e) {
            System.err.println("[LOGGER] Could not open log file: " + e.getMessage());
        }
    }

    /**
     * Returns the singleton instance.
     */
    public static LoggerService getInstance() {
        return INSTANCE;
    }

    /**
     * Logs an informational message.
     */
    public void info(String message) {
        log("INFO", message);
    }

    /**
     * Logs a warning message.
     */
    public void warn(String message) {
        log("WARN", message);
    }

    /**
     * Logs an error message.
     */
    public void error(String message) {
        log("ERROR", message);
    }

    /**
     * Logs an error message with exception details.
     */
    public void error(String message, Throwable throwable) {
        log("ERROR", message + " | Exception: " + throwable.getMessage());
    }

    /**
     * Internal logging method — writes to console and file.
     */
    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logLine = String.format("[%s] [%s] %s", timestamp, level, message);

        // Console output with color
        switch (level) {
            case "INFO" -> System.out.println("\u001B[36m" + logLine + "\u001B[0m");
            case "WARN" -> System.out.println("\u001B[33m" + logLine + "\u001B[0m");
            case "ERROR" -> System.out.println("\u001B[31m" + logLine + "\u001B[0m");
            default -> System.out.println(logLine);
        }

        // File output
        if (fileLoggingEnabled && fileWriter != null) {
            fileWriter.println(logLine);
        }
    }

    /**
     * Closes the file writer on shutdown.
     */
    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }
}
