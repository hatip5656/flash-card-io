package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.service.GrammarBankService;
import io.flashcard.service.GrammarPodcastService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/grammar")
public class AdminGrammarController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final GrammarPodcastService grammarPodcastService;
    private final GrammarBankService grammarBankService;

    public AdminGrammarController(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                  GrammarPodcastService grammarPodcastService,
                                  GrammarBankService grammarBankService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.grammarPodcastService = grammarPodcastService;
        this.grammarBankService = grammarBankService;
    }

    @GetMapping
    public ResponseEntity<?> listAll() {
        var lessons = jdbc.queryForList("""
            SELECT g.id, g.cefr_level, g.topic, g.topic_tr, g.podcast_id,
                   g.podcast_script IS NOT NULL AS has_script,
                   p.status AS podcast_status,
                   p.duration_seconds AS podcast_duration,
                   p.audio_cache_key,
                   p.created_at AS podcast_created_at
            FROM grammar_lessons g
            LEFT JOIN podcasts p ON p.id = g.podcast_id
            ORDER BY g.cefr_level, g.id
            """);

        // Add audio URL for ready podcasts
        for (var lesson : lessons) {
            String audioKey = (String) lesson.get("audio_cache_key");
            if (audioKey != null) {
                lesson.put("audioUrl", "https://wordagram.hatip.dev/podcasts/" + audioKey);
            }
        }

        int total = lessons.size();
        int withScript = (int) lessons.stream().filter(l -> Boolean.TRUE.equals(l.get("has_script"))).count();
        int withPodcast = (int) lessons.stream().filter(l -> l.get("podcast_id") != null).count();

        return ResponseEntity.ok(Map.of(
            "items", lessons,
            "total", total,
            "withScript", withScript,
            "withPodcast", withPodcast,
            "pendingGeneration", withScript - withPodcast
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT g.*, p.status AS podcast_status, p.duration_seconds AS podcast_duration,
                   p.audio_cache_key, p.script AS podcast_timings
            FROM grammar_lessons g
            LEFT JOIN podcasts p ON p.id = g.podcast_id
            WHERE g.id = ?
            """, id);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();

        var lesson = rows.get(0);
        String audioKey = (String) lesson.get("audio_cache_key");
        if (audioKey != null) {
            lesson.put("audioUrl", "https://wordagram.hatip.dev/podcasts/" + audioKey);
        }
        return ResponseEntity.ok(lesson);
    }

    @PatchMapping("/{id}/script")
    public ResponseEntity<?> updateScript(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object scriptObj = body.get("podcast_script");
        if (scriptObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "podcast_script is required"));
        }

        try {
            String scriptJson = objectMapper.writeValueAsString(scriptObj);
            // Validate it's a valid JSON array
            List<?> segments = objectMapper.readValue(scriptJson, List.class);
            if (segments.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Script must be a non-empty array"));
            }

            jdbc.update("UPDATE grammar_lessons SET podcast_script = ?::jsonb WHERE id = ?",
                scriptJson, id);

            return ResponseEntity.ok(Map.of("updated", id, "segments", segments.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/content")
    public ResponseEntity<?> updateContent(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String topic = (String) body.get("topic");
        String topicTr = (String) body.get("topic_tr");
        String content = (String) body.get("content");
        String contentTr = (String) body.get("content_tr");

        jdbc.update("""
            UPDATE grammar_lessons SET
                topic = COALESCE(?, topic),
                topic_tr = COALESCE(?, topic_tr),
                content = COALESCE(?, content),
                content_tr = COALESCE(?, content_tr)
            WHERE id = ?
            """, topic, topicTr, content, contentTr, id);

        grammarBankService.reload();
        return ResponseEntity.ok(Map.of("updated", id));
    }

    @PostMapping("/{id}/redo-podcast")
    public ResponseEntity<?> redoPodcast(@PathVariable String id) {
        // Clear podcast_id so the scheduler picks it up again (if script exists)
        jdbc.update("UPDATE grammar_lessons SET podcast_id = NULL WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("queued", id, "message", "Podcast will be regenerated on next scheduler run"));
    }

    @DeleteMapping("/{id}/podcast")
    public ResponseEntity<?> removePodcast(@PathVariable String id) {
        jdbc.update("UPDATE grammar_lessons SET podcast_id = NULL WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("cleared", id));
    }

    @PostMapping("/cleanup-orphans")
    public ResponseEntity<?> cleanupOrphanPodcasts() {
        // Find WAV files on disk that have no matching podcast record with status='ready'
        // Safety: only delete files whose ID is NOT in the podcasts table
        int deleted = 0;
        try {
            var podcastDir = java.nio.file.Path.of(
                System.getenv("CACHE_DIR") != null ? System.getenv("CACHE_DIR") : "/app/cache",
                "podcasts");
            if (!java.nio.file.Files.isDirectory(podcastDir)) {
                return ResponseEntity.ok(Map.of("deleted", 0, "message", "Podcast directory not found"));
            }

            // Get all valid audio_cache_keys from DB
            var validKeys = new java.util.HashSet<>(
                jdbc.queryForList("SELECT audio_cache_key FROM podcasts WHERE audio_cache_key IS NOT NULL", String.class));
            // Also include grammar-linked podcasts
            var grammarKeys = jdbc.queryForList(
                "SELECT p.audio_cache_key FROM podcasts p JOIN grammar_lessons g ON g.podcast_id = p.id WHERE p.audio_cache_key IS NOT NULL",
                String.class);
            validKeys.addAll(grammarKeys);

            try (var stream = java.nio.file.Files.list(podcastDir)) {
                for (var file : stream.toList()) {
                    String filename = file.getFileName().toString();
                    if (filename.endsWith(".wav") && !validKeys.contains(filename)) {
                        java.nio.file.Files.delete(file);
                        deleted++;
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @GetMapping("/scheduler")
    public ResponseEntity<?> getSchedulerStatus() {
        return ResponseEntity.ok(grammarPodcastService.getStatus());
    }

    @PostMapping("/scheduler/trigger")
    public ResponseEntity<?> triggerScheduler() {
        Thread.startVirtualThread(() -> grammarPodcastService.generateNextGrammarPodcast());
        return ResponseEntity.ok(Map.of("triggered", true));
    }
}
