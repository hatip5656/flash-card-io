package io.flashcard.controller;

import io.flashcard.service.GeminiService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final GeminiService geminiService;

    public ChatbotController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/message")
    public Map<String, Object> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> body) {

        if (!geminiService.isAvailable()) {
            return Map.of("error", "Chatbot not configured");
        }

        String userMessage = (String) body.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        String userLevel = (String) body.getOrDefault("level", "A2");

        if (userMessage.isBlank()) {
            return Map.of("error", "Empty message");
        }

        String reply = geminiService.chat(userMessage, history, userLevel);
        if (reply != null) {
            return Map.of("reply", reply);
        }
        return Map.of("error", "No response from AI");
    }
}
