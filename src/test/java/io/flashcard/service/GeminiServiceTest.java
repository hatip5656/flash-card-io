package io.flashcard.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {

    private static boolean fallsBack(int status, String body) {
        return GeminiService.shouldFallBack(new GeminiService.Response(status, body));
    }

    @Test
    void overloadRateLimitsAndRefusedModelsFallBack() {
        assertTrue(fallsBack(503, "This model is currently experiencing high demand."));
        assertTrue(fallsBack(429, "Resource has been exhausted"));
        assertTrue(fallsBack(404, "models/gemini-9 is not found"));
        assertTrue(fallsBack(400, "The model gemini-x is not supported for generateContent"));
    }

    @Test
    void badRequestsDoNotFallBack() {
        assertFalse(fallsBack(400, "Invalid JSON payload received"));
        assertFalse(fallsBack(401, "API key not valid"));
    }
}
