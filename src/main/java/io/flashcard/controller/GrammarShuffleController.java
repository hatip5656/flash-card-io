package io.flashcard.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/grammar-shuffle")
public class GrammarShuffleController {

    private final JdbcTemplate jdbc;

    public GrammarShuffleController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> getSentences(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "10") int limit) {

        // Get sentences linked to words the user has seen (via sent_words)
        List<Map<String, Object>> sentences = jdbc.queryForList(
            "SELECT gs.id, gs.word_id, gs.cefr_level, gs.estonian, " +
            "gs.estonian_tokens, gs.turkish_tokens, gs.english_tokens, " +
            "gs.turkish, gs.english " +
            "FROM grammar_shuffle gs " +
            "WHERE gs.word_id IN (SELECT word_id FROM sent_words WHERE chat_id = ?) " +
            "ORDER BY RANDOM() LIMIT ?",
            userId, limit);

        // If user hasn't seen enough words, fall back to A1 sentences
        if (sentences.size() < 5) {
            sentences = jdbc.queryForList(
                "SELECT gs.id, gs.word_id, gs.cefr_level, gs.estonian, " +
                "gs.estonian_tokens, gs.turkish_tokens, gs.english_tokens, " +
                "gs.turkish, gs.english " +
                "FROM grammar_shuffle gs " +
                "WHERE gs.cefr_level IN ('A1', 'A2') " +
                "ORDER BY RANDOM() LIMIT ?",
                limit);
        }

        return Map.of("sentences", sentences, "count", sentences.size());
    }

    @PostMapping("/seed")
    public Map<String, Object> seedSentences(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        int inserted = 0;
        int failed = 0;
        for (var item : items) {
            try {
                jdbc.update(
                    "INSERT INTO grammar_shuffle (word_id, cefr_level, estonian, estonian_tokens, turkish_tokens, english_tokens, turkish, english) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    item.get("wordId"), item.get("cefrLevel"), item.get("estonian"),
                    item.get("estonianTokens"), item.get("turkishTokens"), item.get("englishTokens"),
                    item.get("turkish"), item.get("english"));
                inserted++;
            } catch (Exception e) {
                failed++;
            }
        }
        return Map.of("ok", true, "inserted", inserted, "failed", failed);
    }
}
