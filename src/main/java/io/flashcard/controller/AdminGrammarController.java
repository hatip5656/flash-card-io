package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    public AdminGrammarController(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                  GrammarPodcastService grammarPodcastService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.grammarPodcastService = grammarPodcastService;
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

    @DeleteMapping("/{id}/podcast")
    public ResponseEntity<?> removePodcast(@PathVariable String id) {
        // Remove podcast link (doesn't delete the podcast record or audio file)
        jdbc.update("UPDATE grammar_lessons SET podcast_id = NULL WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("cleared", id));
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
