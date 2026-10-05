package io.flashcard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API for managing sentence-grammar links.
 * These connect example sentences to the grammar rules they demonstrate.
 * Designed to be populated by an MCP tool that uses AI analysis.
 */
@RestController
@RequestMapping("/api/admin/sentence-grammar")
public class AdminSentenceGrammarController {

    private final JdbcTemplate jdbc;

    public AdminSentenceGrammarController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * List all links, optionally filtered by word_id or grammar_lesson_id.
     */
    @GetMapping
    public ResponseEntity<?> listLinks(
            @RequestParam(required = false) String wordId,
            @RequestParam(required = false) String grammarId,
            @RequestParam(defaultValue = "100") int limit) {
        String sql;
        Object[] params;

        if (wordId != null) {
            sql = """
                SELECT l.*, g.topic AS grammar_topic, g.cefr_level AS grammar_level
                FROM sentence_grammar_links l
                JOIN grammar_lessons g ON g.id = l.grammar_lesson_id
                WHERE l.word_id = ?
                ORDER BY l.created_at DESC LIMIT ?
                """;
            params = new Object[]{wordId, limit};
        } else if (grammarId != null) {
            sql = """
                SELECT l.*, w.estonian AS word_estonian, w.english AS word_english
                FROM sentence_grammar_links l
                JOIN words w ON w.id = l.word_id
                WHERE l.grammar_lesson_id = ?
                ORDER BY l.created_at DESC LIMIT ?
                """;
            params = new Object[]{grammarId, limit};
        } else {
            sql = """
                SELECT l.*, g.topic AS grammar_topic, g.cefr_level AS grammar_level,
                       w.estonian AS word_estonian
                FROM sentence_grammar_links l
                JOIN grammar_lessons g ON g.id = l.grammar_lesson_id
                JOIN words w ON w.id = l.word_id
                ORDER BY l.created_at DESC LIMIT ?
                """;
            params = new Object[]{limit};
        }

        var links = jdbc.queryForList(sql, params);
        return ResponseEntity.ok(Map.of("items", links, "count", links.size()));
    }

    /**
     * Create a link between a sentence and a grammar lesson.
     * Idempotent — duplicate links are ignored.
     */
    @PostMapping
    public ResponseEntity<?> createLink(@RequestBody Map<String, Object> body) {
        String wordId = (String) body.get("wordId");
        String sentenceEstonian = (String) body.get("sentenceEstonian");
        String grammarLessonId = (String) body.get("grammarLessonId");
        Double confidence = body.get("confidence") != null ? ((Number) body.get("confidence")).doubleValue() : 1.0;
        String notes = (String) body.get("notes");
        String createdBy = (String) body.getOrDefault("createdBy", "manual");

        if (wordId == null || sentenceEstonian == null || grammarLessonId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordId, sentenceEstonian, grammarLessonId required"));
        }

        try {
            jdbc.update("""
                INSERT INTO sentence_grammar_links (word_id, sentence_estonian, grammar_lesson_id, confidence, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (word_id, sentence_estonian, grammar_lesson_id) DO UPDATE SET
                    confidence = EXCLUDED.confidence, notes = EXCLUDED.notes, created_by = EXCLUDED.created_by
                """, wordId, sentenceEstonian, grammarLessonId, confidence, notes, createdBy);

            return ResponseEntity.ok(Map.of("linked", true, "wordId", wordId, "grammarLessonId", grammarLessonId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Bulk create links (for MCP tool batch operations).
     */
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkCreateLinks(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) body.get("links");
        if (links == null || links.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "links array required"));
        }

        int created = 0, failed = 0;
        for (var link : links) {
            try {
                String wordId = (String) link.get("wordId");
                String sentence = (String) link.get("sentenceEstonian");
                String grammarId = (String) link.get("grammarLessonId");
                double confidence = link.get("confidence") != null ? ((Number) link.get("confidence")).doubleValue() : 1.0;
                String notes = (String) link.get("notes");
                String createdBy = (String) link.getOrDefault("createdBy", "mcp-ai");

                if (wordId == null || sentence == null || grammarId == null) { failed++; continue; }

                jdbc.update("""
                    INSERT INTO sentence_grammar_links (word_id, sentence_estonian, grammar_lesson_id, confidence, notes, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (word_id, sentence_estonian, grammar_lesson_id) DO UPDATE SET
                        confidence = EXCLUDED.confidence, notes = EXCLUDED.notes
                    """, wordId, sentence, grammarId, confidence, notes, createdBy);
                created++;
            } catch (Exception e) {
                failed++;
            }
        }

        return ResponseEntity.ok(Map.of("created", created, "failed", failed, "total", links.size()));
    }

    /**
     * Delete a link.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLink(@PathVariable int id) {
        jdbc.update("DELETE FROM sentence_grammar_links WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    /**
     * Get stats about coverage.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        int totalLinks = jdbc.queryForObject("SELECT COUNT(*) FROM sentence_grammar_links", Integer.class);
        int totalSentences = jdbc.queryForObject("SELECT COUNT(DISTINCT (word_id, sentence_estonian)) FROM sentence_grammar_links", Integer.class);
        int totalGrammars = jdbc.queryForObject("SELECT COUNT(DISTINCT grammar_lesson_id) FROM sentence_grammar_links", Integer.class);
        int totalWords = jdbc.queryForObject("SELECT COUNT(DISTINCT word_id) FROM sentence_grammar_links", Integer.class);

        int unlinkedSentences = jdbc.queryForObject("""
            SELECT COUNT(*) FROM word_sentences ws
            WHERE NOT EXISTS (
                SELECT 1 FROM sentence_grammar_links sgl
                WHERE sgl.word_id = ws.word_id AND sgl.sentence_estonian = ws.estonian
            )
            """, Integer.class);

        return ResponseEntity.ok(Map.of(
            "totalLinks", totalLinks,
            "linkedSentences", totalSentences,
            "linkedGrammars", totalGrammars,
            "linkedWords", totalWords,
            "unlinkedSentences", unlinkedSentences
        ));
    }

    /**
     * Get the AI prompt template for analyzing a sentence.
     * The MCP tool can call this to get the prompt, fill in the sentence, and send to AI.
     */
    @GetMapping("/ai-prompt")
    public ResponseEntity<?> getAIPrompt() {
        // Get all grammar lesson topics for the prompt
        var grammars = jdbc.queryForList(
            "SELECT id, cefr_level, topic, topic_tr FROM grammar_lessons ORDER BY cefr_level, id");

        StringBuilder grammarList = new StringBuilder();
        for (var g : grammars) {
            grammarList.append("- ").append(g.get("id")).append(" (").append(g.get("cefr_level")).append("): ")
                .append(g.get("topic"));
            if (g.get("topic_tr") != null) grammarList.append(" / ").append(g.get("topic_tr"));
            grammarList.append("\n");
        }

        String prompt = """
            You are an Estonian grammar expert. Analyze the following Estonian sentence and identify which grammar rules it demonstrates.

            AVAILABLE GRAMMAR LESSONS:
            %s

            SENTENCE TO ANALYZE:
            Estonian: {sentence_estonian}
            English: {sentence_english}
            Word context: {word_estonian} ({word_english})

            For each grammar rule demonstrated in this sentence, return a JSON array:
            [
              {
                "grammarLessonId": "the-lesson-id",
                "confidence": 0.0 to 1.0,
                "notes": "brief explanation of how this grammar rule appears in the sentence"
              }
            ]

            RULES:
            - Only include grammar lessons that are CLEARLY demonstrated in the sentence
            - Confidence 1.0 = the sentence is a textbook example of this rule
            - Confidence 0.7-0.9 = the rule is present but not the focus
            - Confidence < 0.7 = only tangentially related, probably skip
            - Return ONLY the JSON array, no other text
            """.formatted(grammarList.toString());

        return ResponseEntity.ok(Map.of(
            "prompt", prompt,
            "grammarCount", grammars.size(),
            "grammars", grammars
        ));
    }

    /**
     * Get sentences that need grammar analysis (unlinked).
     * The MCP tool can iterate through these.
     */
    @GetMapping("/unlinked")
    public ResponseEntity<?> getUnlinkedSentences(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var sentences = jdbc.queryForList("""
            SELECT ws.word_id, ws.estonian, ws.english, ws.turkish, w.estonian AS word_estonian, w.english AS word_english, w.cefr_level
            FROM word_sentences ws
            JOIN words w ON w.id = ws.word_id
            WHERE NOT EXISTS (
                SELECT 1 FROM sentence_grammar_links sgl
                WHERE sgl.word_id = ws.word_id AND sgl.sentence_estonian = ws.estonian
            )
            ORDER BY w.cefr_level, w.estonian
            LIMIT ? OFFSET ?
            """, limit, offset);

        int total = jdbc.queryForObject("""
            SELECT COUNT(*) FROM word_sentences ws
            WHERE NOT EXISTS (
                SELECT 1 FROM sentence_grammar_links sgl
                WHERE sgl.word_id = ws.word_id AND sgl.sentence_estonian = ws.estonian
            )
            """, Integer.class);

        return ResponseEntity.ok(Map.of("items", sentences, "total", total));
    }
}
