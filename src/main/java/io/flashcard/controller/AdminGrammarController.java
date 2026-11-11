package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.model.GrammarLanguage;
import io.flashcard.service.GrammarBankService;
import io.flashcard.service.GrammarPodcastService;
import io.flashcard.service.PodcastVideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/grammar")
public class AdminGrammarController {

    private static final Logger log = LoggerFactory.getLogger(AdminGrammarController.class);
    private static final String PODCAST_BASE_URL = "https://wordagram.hatip.dev/podcasts/";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final GrammarPodcastService grammarPodcastService;
    private final GrammarBankService grammarBankService;
    private final PodcastVideoService podcastVideoService;
    private final String cacheDir;

    public AdminGrammarController(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                  GrammarPodcastService grammarPodcastService,
                                  GrammarBankService grammarBankService,
                                  PodcastVideoService podcastVideoService,
                                  @org.springframework.beans.factory.annotation.Value("${CACHE_DIR:/app/cache}") String cacheDir) {
        this.cacheDir = cacheDir;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.grammarPodcastService = grammarPodcastService;
        this.grammarBankService = grammarBankService;
        this.podcastVideoService = podcastVideoService;
    }

    /** Per-language podcast and video fields of a lesson, as the admin lists them. */
    private static final List<String> MEDIA_FIELDS = List.of(
        "podcast_id", "has_script", "script_segments", "script_updated_at", "podcast_status", "podcast_duration",
        "podcast_segments", "podcast_created_at", "audioUrl", "video_status", "video_progress", "video_url",
        "video_error", "video_requested_at", "video_started_at", "video_completed_at");

    /** An invalid {@code lang} parameter. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /**
     * Every lesson with its podcast and video per language under {@code media.tr} / {@code media.en}.
     * The top-level podcast and video fields are the Turkish ones, as before English existed.
     */
    @GetMapping
    public ResponseEntity<?> listAll() {
        var lessons = jdbc.queryForList(
            "SELECT g.id, g.cefr_level, g.topic, g.topic_tr FROM grammar_lessons g ORDER BY g.cefr_level, g.id");

        Map<String, Map<String, Map<String, Object>>> mediaByLesson = new HashMap<>();
        for (var row : jdbc.queryForList("""
                SELECT gp.lesson_id, gp.language, gp.podcast_id,
                       gp.script IS NOT NULL AS has_script,
                       CASE WHEN jsonb_typeof(gp.script) = 'array' THEN jsonb_array_length(gp.script) END AS script_segments,
                       gp.script_updated_at,
                       p.status AS podcast_status,
                       p.duration_seconds AS podcast_duration,
                       p.audio_cache_key,
                       p.created_at AS podcast_created_at,
                       CASE WHEN jsonb_typeof(p.script) = 'array' AND jsonb_array_length(p.script) > 0
                            THEN jsonb_array_length(p.script) END AS podcast_segments,
                       v.status AS video_status, v.progress AS video_progress, v.video_url, v.error AS video_error,
                       v.requested_at AS video_requested_at, v.started_at AS video_started_at,
                       v.completed_at AS video_completed_at
                FROM grammar_lesson_podcasts gp
                LEFT JOIN podcasts p ON p.id = gp.podcast_id
                LEFT JOIN podcast_videos v ON v.podcast_id = gp.podcast_id
                """)) {
            var media = new LinkedHashMap<String, Object>();
            for (String field : MEDIA_FIELDS) media.put(field, row.get(field));
            media.put("has_script", Boolean.TRUE.equals(row.get("has_script")));
            String audioKey = (String) row.get("audio_cache_key");
            media.put("audioUrl", audioKey != null ? PODCAST_BASE_URL + audioKey : null);
            mediaByLesson.computeIfAbsent((String) row.get("lesson_id"), k -> new HashMap<>())
                .put((String) row.get("language"), media);
        }

        Map<String, Map<String, Integer>> stats = new LinkedHashMap<>();
        for (String lang : GrammarLanguage.ALL) stats.put(lang, new LinkedHashMap<>(Map.of("withScript", 0, "withPodcast", 0)));
        for (var lesson : lessons) {
            var byLang = mediaByLesson.getOrDefault((String) lesson.get("id"), Map.of());
            var media = new LinkedHashMap<String, Object>();
            for (String lang : GrammarLanguage.ALL) {
                var m = byLang.getOrDefault(lang, emptyMedia());
                media.put(lang, m);
                if (Boolean.TRUE.equals(m.get("has_script"))) stats.get(lang).merge("withScript", 1, Integer::sum);
                if (m.get("podcast_id") != null) stats.get(lang).merge("withPodcast", 1, Integer::sum);
            }
            lesson.putAll((Map<String, Object>) media.get("tr"));
            lesson.put("media", media);
        }
        for (var st : stats.values()) st.put("pendingGeneration", st.get("withScript") - st.get("withPodcast"));

        // Renders tracked by podcast, including ones whose podcast was regenerated meanwhile
        var activeVideos = jdbc.queryForList("""
            SELECT v.podcast_id, v.lesson_id, gp.language, g.cefr_level, g.topic, v.status, v.progress,
                   v.requested_at, v.started_at, p.duration_seconds AS podcast_duration
            FROM podcast_videos v
            LEFT JOIN grammar_lessons g ON g.id = v.lesson_id
            LEFT JOIN grammar_lesson_podcasts gp ON gp.podcast_id = v.podcast_id
            LEFT JOIN podcasts p ON p.id = v.podcast_id
            WHERE v.status IN ('queued', 'rendering')
            ORDER BY v.requested_at
            """);

        var tr = stats.get("tr");
        var result = new LinkedHashMap<String, Object>();
        result.put("items", lessons);
        result.put("activeVideos", activeVideos);
        result.put("total", lessons.size());
        result.put("withScript", tr.get("withScript"));
        result.put("withPodcast", tr.get("withPodcast"));
        result.put("pendingGeneration", tr.get("pendingGeneration"));
        result.put("statsByLanguage", stats);
        return ResponseEntity.ok(result);
    }

    private static Map<String, Object> emptyMedia() {
        var m = new LinkedHashMap<String, Object>();
        for (String field : MEDIA_FIELDS) m.put(field, null);
        m.put("has_script", false);
        return m;
    }

    /** The lesson with one language's script and podcast ({@code lang}, Turkish by default). */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable String id, @RequestParam(required = false) String lang) {
        String language = GrammarLanguage.parse(lang);
        var rows = jdbc.queryForList("SELECT g.* FROM grammar_lessons g WHERE g.id = ?", id);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();

        var lesson = new LinkedHashMap<String, Object>(rows.get(0));
        // The old single-language columns are not the lesson's podcast any more
        lesson.keySet().removeAll(List.of("podcast_id", "podcast_script", "script_updated_at"));
        lesson.put("language", language);
        var media = jdbc.queryForList("""
            SELECT gp.podcast_id, gp.script AS podcast_script, gp.script_updated_at,
                   p.status AS podcast_status, p.duration_seconds AS podcast_duration,
                   p.audio_cache_key, p.script AS podcast_timings
            FROM grammar_lessons g
            JOIN grammar_lesson_podcasts gp ON gp.lesson_id = g.id AND gp.language = ?
            LEFT JOIN podcasts p ON p.id = gp.podcast_id
            WHERE g.id = ?
            """, language, id);
        if (!media.isEmpty()) lesson.putAll(media.get(0));

        String audioKey = (String) lesson.get("audio_cache_key");
        if (audioKey != null) {
            lesson.put("audioUrl", PODCAST_BASE_URL + audioKey);
        }
        // Parse JSONB fields into proper JSON for the frontend
        for (String key : List.of("podcast_timings", "podcast_script")) {
            Object obj = lesson.get(key);
            if (obj != null) {
                try {
                    lesson.put(key, objectMapper.readValue(obj.toString(), List.class));
                } catch (Exception ignored) {}
            }
        }
        return ResponseEntity.ok(lesson);
    }

    @PatchMapping("/{id}/script")
    public ResponseEntity<?> updateScript(@PathVariable String id, @RequestParam(required = false) String lang,
                                          @RequestBody Map<String, Object> body) {
        String language = GrammarLanguage.parse(lang);
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

            jdbc.update("""
                INSERT INTO grammar_lesson_podcasts (lesson_id, language, script, script_updated_at)
                VALUES (?, ?, ?::jsonb, NOW())
                ON CONFLICT (lesson_id, language) DO UPDATE SET script = EXCLUDED.script, script_updated_at = NOW()
                """, id, language, scriptJson);

            return ResponseEntity.ok(Map.of("updated", id, "language", language, "segments", segments.size()));
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
    public ResponseEntity<?> redoPodcast(@PathVariable String id, @RequestParam(required = false) String lang) {
        String language = GrammarLanguage.parse(lang);
        // Clear that language's podcast so the generator picks it up again (if its script exists)
        clearPodcast(id, language);
        Thread.startVirtualThread(grammarPodcastService::generateAllPending);
        return ResponseEntity.ok(Map.of("queued", id, "language", language, "message", "Podcast is being regenerated"));
    }

    @PostMapping("/redo-podcasts")
    public ResponseEntity<?> redoPodcasts(@RequestParam(required = false) String lang,
                                          @RequestBody Map<String, List<String>> body) {
        String language = GrammarLanguage.parse(lang);
        List<String> ids = body.get("ids") == null ? List.of() : body.get("ids").stream().distinct().toList();
        if (ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "ids is required"));
        // Lessons without a script would lose their podcast and never get a new one
        List<String> queued = jdbc.queryForList("""
            UPDATE grammar_lesson_podcasts SET podcast_id = NULL
            WHERE lesson_id = ANY(?) AND language = ? AND script IS NOT NULL RETURNING lesson_id
            """, String.class, ids.toArray(String[]::new), language);
        if (!queued.isEmpty()) Thread.startVirtualThread(grammarPodcastService::generateAllPending);
        return ResponseEntity.ok(Map.of("queued", queued, "skipped", ids.size() - queued.size(), "language", language));
    }

    @DeleteMapping("/{id}/podcast")
    public ResponseEntity<?> removePodcast(@PathVariable String id, @RequestParam(required = false) String lang) {
        String language = GrammarLanguage.parse(lang);
        clearPodcast(id, language);
        return ResponseEntity.ok(Map.of("cleared", id, "language", language));
    }

    private void clearPodcast(String lessonId, String language) {
        jdbc.update("UPDATE grammar_lesson_podcasts SET podcast_id = NULL WHERE lesson_id = ? AND language = ?",
            lessonId, language);
    }

    @PostMapping("/{id}/video")
    public ResponseEntity<?> generateVideo(@PathVariable String id, @RequestParam(required = false) String lang,
                                           @RequestParam(defaultValue = "false") boolean force) {
        var podcast = findPodcast(id, GrammarLanguage.parse(lang));
        if (podcast == null) return ResponseEntity.notFound().build();
        if (podcast.get("podcast_id") == null || !"ready".equals(podcast.get("podcast_status"))
                || podcast.get("audio_cache_key") == null) {
            return ResponseEntity.status(409).body(Map.of("error", "Lesson has no ready podcast"));
        }

        List<Map<String, Object>> subtitles;
        try {
            subtitles = toSubtitles(podcast.get("podcast_timings"));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(Map.of("error", "Podcast timings are not valid JSON"));
        }
        if (subtitles.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("error", "Podcast has no timings"));
        }

        String podcastId = podcast.get("podcast_id").toString();
        try {
            return ResponseEntity.ok(podcastVideoService.request(
                id, podcastId, PODCAST_BASE_URL + podcast.get("audio_cache_key"), subtitles, force));
        } catch (Exception e) {
            log.warn("Video request for lesson {} failed: {}", id, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Video service unavailable: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/video")
    public ResponseEntity<?> getVideo(@PathVariable String id, @RequestParam(required = false) String lang) {
        var podcast = findPodcast(id, GrammarLanguage.parse(lang));
        if (podcast == null) return ResponseEntity.notFound().build();
        if (podcast.get("podcast_id") == null) return ResponseEntity.ok(Map.of("status", "none"));

        return ResponseEntity.ok(podcastVideoService.statusFor(podcast.get("podcast_id").toString()));
    }

    /** A lesson's podcast in one language; null when the lesson does not exist. */
    private Map<String, Object> findPodcast(String lessonId, String language) {
        var rows = jdbc.queryForList("""
            SELECT gp.podcast_id, p.status AS podcast_status, p.audio_cache_key, p.script AS podcast_timings
            FROM grammar_lessons g
            LEFT JOIN grammar_lesson_podcasts gp ON gp.lesson_id = g.id AND gp.language = ?
            LEFT JOIN podcasts p ON p.id = gp.podcast_id
            WHERE g.id = ?
            """, language, lessonId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toSubtitles(Object timings) throws Exception {
        if (timings == null) return List.of();
        List<Map<String, Object>> raw = objectMapper.readValue(timings.toString(), List.class);
        return raw.stream().map(t -> {
            var s = new LinkedHashMap<String, Object>();
            s.put("text", t.get("text"));
            s.put("language", t.get("language"));
            s.put("start_ms", t.get("start_ms"));
            s.put("end_ms", t.get("end_ms"));
            return (Map<String, Object>) s;
        }).toList();
    }

    @PostMapping("/cleanup-orphans")
    public ResponseEntity<?> cleanupOrphanPodcasts() {
        // Find WAV files on disk that have no matching podcast record with status='ready'
        // Safety: only delete files whose ID is NOT in the podcasts table
        int deleted = 0;
        try {
            var podcastDir = java.nio.file.Path.of(cacheDir, "podcasts");
            if (!java.nio.file.Files.isDirectory(podcastDir)) {
                return ResponseEntity.ok(Map.of("deleted", 0, "message", "Podcast directory not found"));
            }

            // Every podcast's audio is kept, grammar podcasts of every language included
            var validKeys = new java.util.HashSet<>(
                jdbc.queryForList("SELECT audio_cache_key FROM podcasts WHERE audio_cache_key IS NOT NULL", String.class));

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
