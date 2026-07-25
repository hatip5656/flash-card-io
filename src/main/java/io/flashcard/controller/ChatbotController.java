package io.flashcard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final JdbcTemplate jdbc;

    @Value("${app.gemini-api-key:}")
    private String geminiApiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";

    private static final String SYSTEM_PROMPT = """
            You are an Estonian language tutor named Keeleabi ("Language Helper"). You help users prepare for official Estonian language proficiency exams (A2, B1, B2 levels).

            Your capabilities:
            1. CONVERSATION PRACTICE: Engage in Estonian dialogues matching exam speaking scenarios (at the doctor, post office, shop, restaurant, etc.)
            2. GRAMMAR CORRECTION: When the user writes Estonian, correct mistakes gently and explain the grammar rule in their native language
            3. VOCABULARY HELP: Explain Estonian words with example sentences, etymology, and usage
            4. WRITING FEEDBACK: Evaluate exam-style writing tasks (letters, descriptions, essays)
            5. EXAM TIPS: Share strategies for each exam section (reading, writing, listening, speaking)
            6. ROLE PLAY: Simulate exam speaking tasks where you play the examiner

            Rules:
            - Detect the user's native language (Turkish or English) and use it for explanations
            - Write Estonian text in Estonian, explanations in the user's language
            - Keep responses concise — this is a mobile chat
            - Use the user's CEFR level to adjust complexity
            - When correcting grammar, show: wrong → correct, then explain why
            - Be encouraging and supportive
            - If the user seems stuck, offer hints rather than full answers
            - For role-plays, stay in character as the examiner until the user says stop

            Format:
            - Use simple formatting (no markdown headers, keep it chat-friendly)
            - Estonian words/phrases in quotes when embedded in explanations
            - Short paragraphs, max 3-4 sentences each
            """;

    public ChatbotController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/message")
    public Map<String, Object> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return Map.of("error", "Chatbot not configured");
        }

        String userMessage = (String) body.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) body.getOrDefault("history", List.of());
        String userLevel = (String) body.getOrDefault("level", "A2");

        if (userMessage.isBlank()) {
            return Map.of("error", "Empty message");
        }

        try {
            // Build Gemini request with conversation history
            List<Map<String, Object>> contents = new ArrayList<>();

            // System instruction as first user message
            String systemWithLevel = SYSTEM_PROMPT + "\nThe user's current CEFR level is: " + userLevel;

            // Add history
            for (var msg : history) {
                String role = (String) msg.getOrDefault("role", "user");
                String text = (String) msg.getOrDefault("text", "");
                contents.add(Map.of(
                    "role", role.equals("bot") ? "model" : "user",
                    "parts", List.of(Map.of("text", text))
                ));
            }

            // Add current message
            contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMessage))
            ));

            // Build request body
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", systemWithLevel))
            ));
            requestBody.put("contents", contents);
            requestBody.put("generationConfig", Map.of(
                "maxOutputTokens", 2048,
                "temperature", 0.7
            ));

            // Call Gemini API
            String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestBody);
            URL url = new URL(GEMINI_URL + "?key=" + geminiApiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String responseBody;
            try (var is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (status != 200) {
                var errorMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseBody, Map.class);
                var error = (Map) errorMap.getOrDefault("error", Map.of());
                return Map.of("error", error.getOrDefault("message", "Gemini API error: " + status));
            }

            var responseMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseBody, Map.class);
            var candidates = (List<Map>) responseMap.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return Map.of("error", "No response from AI");
            }

            var content = (Map) candidates.get(0).get("content");
            var parts = (List<Map>) content.get("parts");
            String reply = (String) parts.get(0).get("text");

            return Map.of("reply", reply);

        } catch (Exception e) {
            return Map.of("error", "Chat error: " + e.getMessage());
        }
    }
}
