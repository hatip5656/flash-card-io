package io.flashcard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${app.gemini-api-key:}")
    private String geminiApiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";

    private static final String SYSTEM_PROMPT = """
            You are an Estonian language tutor named Keeleabi ("Language Helper"). You help users prepare for official Estonian language proficiency exams (A2, B1, B2 levels).

            Your capabilities:
            1. CONVERSATION PRACTICE: Engage in Estonian dialogues matching exam speaking scenarios
            2. GRAMMAR CORRECTION: When the user writes Estonian, correct mistakes and explain the grammar rule
            3. VOCABULARY HELP: Explain Estonian words with example sentences
            4. WRITING FEEDBACK: Evaluate exam-style writing tasks
            5. EXAM TIPS: Share strategies for each exam section
            6. ROLE PLAY: Simulate exam speaking tasks where you play the examiner

            Rules:
            - Detect the user's native language (Turkish or English) and use it for explanations
            - Write Estonian text in Estonian, explanations in the user's language
            - Keep responses concise
            - Use the user's CEFR level to adjust complexity
            - When correcting grammar, show: wrong → correct, then explain why
            - Be encouraging and supportive

            Format:
            - Use simple formatting, no markdown headers
            - Estonian words/phrases in quotes when embedded in explanations
            - Short paragraphs, max 3-4 sentences each
            """;

    public boolean isAvailable() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public String chat(String message, List<Map<String, String>> history, String level) {
        if (!isAvailable()) return null;

        try {
            List<Map<String, Object>> contents = new ArrayList<>();

            for (var msg : history) {
                String role = msg.getOrDefault("role", "user");
                String text = msg.getOrDefault("text", "");
                contents.add(Map.of(
                    "role", "bot".equals(role) ? "model" : "user",
                    "parts", List.of(Map.of("text", text))
                ));
            }

            contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", message))
            ));

            String systemWithLevel = SYSTEM_PROMPT + "\nThe user's current CEFR level is: " + level;

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemWithLevel))));
            requestBody.put("contents", contents);
            requestBody.put("generationConfig", Map.of("maxOutputTokens", 2048, "temperature", 0.7));

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
                log.error("[gemini] API error {}: {}", status, responseBody.substring(0, Math.min(200, responseBody.length())));
                return null;
            }

            var responseMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(responseBody, Map.class);
            var candidates = (List<Map>) responseMap.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;

            var content = (Map) candidates.get(0).get("content");
            var parts = (List<Map>) content.get("parts");
            return (String) parts.get(0).get("text");

        } catch (Exception e) {
            log.error("[gemini] Error: {}", e.getMessage());
            return null;
        }
    }
}
