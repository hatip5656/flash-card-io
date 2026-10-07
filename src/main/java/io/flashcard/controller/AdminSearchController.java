package io.flashcard.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/search")
public class AdminSearchController {

    private final JdbcTemplate jdbc;

    public AdminSearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
        if (q == null || q.isBlank() || q.length() < 2) {
            return Map.of("words", List.of(), "users", List.of(), "grammar", List.of(), "candidates", List.of());
        }

        String pattern = "%" + q.toLowerCase() + "%";
        int cap = Math.min(limit, 10);

        List<Map<String, Object>> words = jdbc.queryForList(
            "SELECT id, estonian, english, turkish, cefr_level FROM words WHERE LOWER(estonian) LIKE ? OR LOWER(english) LIKE ? OR LOWER(turkish) LIKE ? LIMIT ?",
            pattern, pattern, pattern, cap);

        List<Map<String, Object>> users = jdbc.queryForList(
            "SELECT chat_id, first_name, username, cefr_level, channel FROM subscribers WHERE LOWER(first_name) LIKE ? OR LOWER(username) LIKE ? OR CAST(chat_id AS TEXT) LIKE ? LIMIT ?",
            pattern, pattern, pattern, cap);

        List<Map<String, Object>> grammar = jdbc.queryForList(
            "SELECT id, cefr_level, topic, topic_tr FROM grammar_lessons WHERE LOWER(topic) LIKE ? OR LOWER(topic_tr) LIKE ? LIMIT ?",
            pattern, pattern, cap);

        List<Map<String, Object>> candidates = jdbc.queryForList(
            "SELECT id, estonian, english, turkish, cefr_level, status FROM candidate_words WHERE LOWER(estonian) LIKE ? OR LOWER(english) LIKE ? OR LOWER(turkish) LIKE ? LIMIT ?",
            pattern, pattern, pattern, cap);

        return Map.of("words", words, "users", users, "grammar", grammar, "candidates", candidates);
    }
}
