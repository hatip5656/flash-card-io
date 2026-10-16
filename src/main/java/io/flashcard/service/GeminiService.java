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

    // The model per feature lives in learning_settings (llm.gemini_model_chat / _generation / _fallbacks)
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final LearningSettings settings;

    public GeminiService(LearningSettings settings) {
        this.settings = settings;
    }

    private static final String SYSTEM_PROMPT = """
            You are an Estonian language tutor named Keeleabi ("Language Helper"). You help users learn Estonian and prepare for proficiency exams.

            Your capabilities:
            1. CONVERSATION PRACTICE: Engage in Estonian dialogues, naturally using words the learner is struggling with
            2. GRAMMAR CORRECTION: When the user writes Estonian, correct mistakes and explain the grammar rule
            3. VOCABULARY HELP: Prioritize explaining and practicing the learner's weak words
            4. WRITING FEEDBACK: Evaluate writing, suggest using recently learned vocabulary
            5. EXAM TIPS: Share strategies for each exam section
            6. ROLE PLAY: Simulate exam speaking tasks, weaving in words the learner needs to practice

            Personalization rules:
            - Use the learner's native language for explanations
            - When giving examples or practice, PREFER using their weak/missed words so they get extra exposure
            - Adjust complexity to their actual quiz performance, not just their CEFR level
            - If their quiz avg is low (<70%), simplify explanations and use more repetition
            - If their quiz avg is high (>85%), challenge them with harder constructions
            - Reference their progress encouragingly ("You've learned X words!" or "Your streak is impressive!")
            - When correcting grammar, show: wrong -> correct, then explain why

            Format:
            - Use simple formatting, no markdown headers
            - Estonian words/phrases in quotes when embedded in explanations
            - Short paragraphs, max 3-4 sentences each
            - Be encouraging and supportive
            """;

    public boolean isAvailable() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    /**
     * One prompt, a JSON answer (Gemini's JSON mode). Used for generated content such as test drafts.
     * Returns the raw JSON text, or null when Gemini is unavailable or fails.
     */
    public String generateJson(String prompt, int maxTokens, double temperature) {
        if (!isAvailable()) return null;
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
        requestBody.put("generationConfig", Map.of(
            "maxOutputTokens", maxTokens, "temperature", temperature, "responseMimeType", "application/json"));
        return call("llm.gemini_model_generation", requestBody, 180000);
    }

    public String chat(String message, List<Map<String, String>> history, String learnerContext) {
        return chat(message, history, learnerContext, 2048);
    }

    public String chat(String message, List<Map<String, String>> history, String learnerContext, int maxTokens) {
        if (!isAvailable()) return null;

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

        String systemWithContext = SYSTEM_PROMPT + "\n--- LEARNER PROFILE ---\n" + learnerContext;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemWithContext))));
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", Map.of("maxOutputTokens", maxTokens, "temperature", 0.7));
        return call("llm.gemini_model_chat", requestBody, maxTokens > 2048 ? 90000 : 30000);
    }

    record Response(int status, String body) {}

    /**
     * Sends the request to the model stored under modelSetting; if Google refuses that model (not found, not
     * available to this key) or it is overloaded or rate-limited, tries each model of llm.gemini_model_fallbacks
     * in order.
     * Returns the answer text or null.
     */
    private String call(String modelSetting, Map<String, Object> requestBody, int readTimeoutMs) {
        try {
            String jsonBody = MAPPER.writeValueAsString(requestBody);
            String model = settings.text(modelSetting);
            Response r = post(model, jsonBody, readTimeoutMs);
            // Each model has its own capacity: when one is overloaded or refused, walk the fallback chain
            for (String fallback : settings.list("llm.gemini_model_fallbacks")) {
                if (r.status() == 200 || !shouldFallBack(r)) break;
                if (fallback.equals(model)) continue;
                log.warn("[gemini] {} answered {}, trying {}", model, r.status(), fallback);
                model = fallback;
                r = post(model, jsonBody, readTimeoutMs);
            }
            if (r.status() == 200) log.debug("[gemini] answered by {}", model);
            if (r.status() != 200) {
                log.error("[gemini] API error {}: {}", r.status(), r.body().substring(0, Math.min(300, r.body().length())));
                return null;
            }
            return answerText(r.body());
        } catch (Exception e) {
            log.error("[gemini] Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Worth trying the fallback model: the configured one is overloaded (503), rate-limited (429), failing (500)
     * or refused (404, or a 400/403 about the model itself). Other 400s are problems with the request.
     */
    static boolean shouldFallBack(Response r) {
        if (r.status() == 404 || r.status() == 429 || r.status() == 500 || r.status() == 503) return true;
        String b = r.body().toLowerCase(Locale.ROOT);
        return (r.status() == 400 || r.status() == 403) && b.contains("model");
    }

    private Response post(String model, String jsonBody, int readTimeoutMs) throws Exception {
        URL url = new URL(GEMINI_BASE + model + ":generateContent?key=" + geminiApiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(readTimeoutMs);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        try (var is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            return new Response(status, is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** The answer is the last non-thought text part (thinking models may return several parts). */
    @SuppressWarnings("unchecked")
    private static String answerText(String responseBody) throws Exception {
        var responseMap = MAPPER.readValue(responseBody, Map.class);
        var candidates = (List<Map>) responseMap.get("candidates");
        if (candidates == null || candidates.isEmpty()) return null;
        var content = (Map) candidates.get(0).get("content");
        if (content == null || !(content.get("parts") instanceof List<?> parts)) return null;
        String text = null;
        for (Object o : parts) {
            if (o instanceof Map<?, ?> part && part.get("text") instanceof String t && !Boolean.TRUE.equals(part.get("thought"))) text = t;
        }
        return text;
    }
}
