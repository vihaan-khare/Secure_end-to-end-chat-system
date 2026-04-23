package com.chatapp.service;

import com.chatapp.util.LoggerService;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI Chat Service integrating with OpenRouter API for AI-powered responses.
 * Demonstrates:
 * - External API integration
 * - Java HttpClient usage
 * - Graceful degradation when API key is not configured
 */
public class AIService {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    private final String apiKey;
    private final HttpClient httpClient;
    private final LoggerService logger = LoggerService.getInstance();
    private final boolean isConfigured;

    public AIService() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.apiKey = dotenv.get("OPENROUTER_API_KEY", "");
        this.isConfigured = apiKey != null && !apiKey.isBlank();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (isConfigured) {
            logger.info("AIService initialized with OpenRouter API");
        } else {
            logger.warn("AIService: No API key configured. AI features disabled.");
        }
    }

    /**
     * Checks if the AI service is available.
     */
    public boolean isAvailable() {
        return isConfigured;
    }

    /**
     * Sends a user message to the AI and returns the response.
     * Uses the free `meta-llama/llama-3.1-8b-instruct:free` model.
     *
     * @param userMessage The message content from the user
     * @return AI response text, or an error/unavailable message
     */
    public String getAIResponse(String userMessage) {
        if (!isConfigured) {
            return "🤖 AI is not configured. Set OPENROUTER_API_KEY in the .env file to enable AI chat.";
        }

        try {
            // Build the JSON request body
            String requestBody = """
                    {
                        "model": "meta-llama/llama-3.1-8b-instruct:free",
                        "messages": [
                            {
                                "role": "system",
                                "content": "You are a helpful, friendly AI assistant in a group chat application. Keep responses concise (2-3 sentences max). Be conversational and engaging."
                            },
                            {
                                "role": "user",
                                "content": "%s"
                            }
                        ],
                        "max_tokens": 200,
                        "temperature": 0.7
                    }
                    """
                    .formatted(escapeJson(userMessage));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENROUTER_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://chatapp.local")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractContent(response.body());
            } else {
                logger.error("AI API error: " + response.statusCode() + " - " + response.body());
                return "🤖 AI request failed (status " + response.statusCode() + "). Please try again later.";
            }

        } catch (IOException | InterruptedException e) {
            logger.error("AI API request failed", e);
            return "🤖 Could not reach AI service. Please try again later.";
        }
    }

    /**
     * Extracts the response content from the OpenRouter API JSON response.
     * Simple parsing to avoid adding a JSON library dependency for this.
     */
    private String extractContent(String jsonResponse) {
        try {
            // Parse using Gson
            com.google.gson.JsonObject json = com.google.gson.JsonParser
                    .parseString(jsonResponse).getAsJsonObject();
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
        } catch (Exception e) {
            logger.error("Failed to parse AI response", e);
            return "🤖 Sorry, I couldn't process that response.";
        }
    }

    /**
     * Escapes special characters in a string for JSON embedding.
     */
    private String escapeJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
