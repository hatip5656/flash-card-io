package io.flashcard.controller;

import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.GrammarRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.GrammarBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api")
public class FlashcardController {

    private static final String PODCAST_BASE_URL = "https://wordagram.hatip.dev/podcasts/";
    // The teacher-simulator renderer prepends this intro to every lesson video
    // (bookends.introSec in its pipeline), so video time runs ahead of the subtitles.
    private static final int VIDEO_INTRO_OFFSET_MS = 1500;
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    private final SubscriberRepository subscriberRepo;
    private final SentWordRepository sentWordRepo;
    private final GrammarRepository grammarRepo;
    private final GrammarBankService grammarBankService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public FlashcardController(SubscriberRepository subscriberRepo, SentWordRepository sentWordRepo,
                               GrammarRepository grammarRepo, GrammarBankService grammarBankService,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.subscriberRepo = subscriberRepo;
        this.sentWordRepo = sentWordRepo;
        this.grammarRepo = grammarRepo;
        this.grammarBankService = grammarBankService;
        this.jdbc = jdbc;
    }

    @GetMapping("/flashcards/next")
    public ResponseEntity<Map<String, Object>> getNextFlashcard(HttpServletRequest request) {
        // Pre-built queue and live build logic are bot-specific (Telegram).
        // The Java API version returns 503 — flashcard building requires external TTS/image services
        // that are wired via the bot main loop in the Node.js version.
        return ResponseEntity.status(503).body(Map.of("error", "Flashcard building not available via API-only mode. Use the feed endpoint instead."));
    }

    @GetMapping("/flashcards/audio/latest")
    public ResponseEntity<Map<String, Object>> getAudio(HttpServletRequest request) {
        return ResponseEntity.status(404).body(Map.of("error", "No audio available"));
    }

    @GetMapping("/flashcards/grammar")
    public ResponseEntity<?> getGrammarCard(HttpServletRequest request) {
        long chatId = getUserId(request);
        String level = subscriberRepo.getSubscriberLevel(chatId);
        Set<String> sentIds = grammarRepo.getSentGrammarIds(chatId);
        GrammarLesson lesson = grammarBankService.getRandomLesson(level, sentIds);

        if (lesson == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No grammar lessons available for your level"));
        }

        grammarRepo.markGrammarSent(chatId, lesson.id());
        return ResponseEntity.ok(Map.of(
            "id", lesson.id(),
            "topic", lesson.topic(),
            "cefrLevel", lesson.cefrLevel(),
            "content", lesson.content()));
    }

    @GetMapping("/flashcards/grammar-feed")
    public List<Map<String, Object>> getGrammarFeed(HttpServletRequest request,
                                                      @RequestParam(defaultValue = "3") int limit) {
        long chatId = getUserId(request);
        String level = subscriberRepo.getSubscriberLevel(chatId);
        var podcastMap = loadPodcastInfo();
        var all = grammarBankService.getAllLessons().stream()
            .filter(l -> l.cefrLevel().equals(level) || compareLevels(l.cefrLevel(), level) < 0)
            .limit(Math.min(limit, 10))
            .<Map<String, Object>>map(l -> {
                var map = new java.util.LinkedHashMap<String, Object>();
                map.put("id", l.id());
                map.put("topic", l.topic());
                map.put("topicTr", l.topicTr() != null ? l.topicTr() : l.topic());
                map.put("cefrLevel", l.cefrLevel());
                map.put("content", l.content());
                map.put("contentTr", l.contentTr() != null ? l.contentTr() : l.content());
                // Include podcast URL and video flag if the lesson has an associated podcast
                var podcast = podcastMap.get(l.id());
                if (podcast != null) {
                    map.put("podcastUrl", podcast.get("podcastUrl"));
                }
                map.put("hasVideo", podcast != null && Boolean.TRUE.equals(podcast.get("hasVideo")));
                return map;
            })
            .toList();
        return all;
    }

    private int compareLevels(String a, String b) {
        var order = List.of("A1", "A2", "B1", "B2");
        return order.indexOf(a) - order.indexOf(b);
    }

    @PostMapping("/flashcards/grammar-feed/{lessonId}/read")
    public Map<String, Object> markGrammarLessonRead(HttpServletRequest request, @PathVariable String lessonId) {
        long chatId = getUserId(request);
        grammarRepo.markGrammarSent(chatId, lessonId);
        return Map.of("ok", true, "lessonId", lessonId);
    }

    @GetMapping("/grammar/lessons")
    public Map<String, Object> getAllGrammarLessons(HttpServletRequest request) {
        long chatId = getUserId(request);
        Set<String> readIds = grammarRepo.getSentGrammarIds(chatId);
        var allLessons = grammarBankService.getAllLessons();

        // Batch-fetch podcast info for all lessons
        var podcastMap = loadPodcastInfo();

        // Batch-fetch linked example sentences for all grammar lessons (up to 5 per lesson)
        Map<String, List<Map<String, String>>> sentencesMap = new java.util.LinkedHashMap<>();
        try {
            var sentRows = jdbc.queryForList("""
                SELECT sgl.grammar_lesson_id, sgl.sentence_estonian,
                       ws.english AS sentence_english, ws.turkish AS sentence_turkish,
                       w.estonian AS word
                FROM sentence_grammar_links sgl
                JOIN word_sentences ws ON ws.word_id = sgl.word_id AND ws.estonian = sgl.sentence_estonian
                JOIN words w ON w.id = sgl.word_id
                WHERE sgl.confidence >= 0.8
                ORDER BY (CASE WHEN ws.turkish IS NOT NULL AND ws.turkish != '' THEN 0 ELSE 1 END),
                         sgl.confidence DESC
                """);
            for (var row : sentRows) {
                String lessonId = (String) row.get("grammar_lesson_id");
                var list = sentencesMap.computeIfAbsent(lessonId, k -> new java.util.ArrayList<>());
                if (list.size() < 5) {
                    var sent = new java.util.LinkedHashMap<String, String>();
                    sent.put("estonian", (String) row.get("sentence_estonian"));
                    sent.put("english", (String) row.get("sentence_english"));
                    sent.put("turkish", (String) row.get("sentence_turkish"));
                    sent.put("word", (String) row.get("word"));
                    list.add(sent);
                }
            }
        } catch (Exception ignored) {}

        var items = allLessons.stream().map(l -> {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("id", l.id());
            map.put("topic", l.topic());
            map.put("topicTr", l.topicTr() != null ? l.topicTr() : l.topic());
            map.put("cefrLevel", l.cefrLevel());
            map.put("content", l.content());
            map.put("contentTr", l.contentTr() != null ? l.contentTr() : l.content());
            map.put("isRead", readIds.contains(l.id()));
            var podcast = podcastMap.get(l.id());
            map.put("podcastUrl", podcast != null ? podcast.get("podcastUrl") : null);
            map.put("durationSeconds", podcast != null ? podcast.get("durationSeconds") : null);
            map.put("podcastId", podcast != null ? podcast.get("podcastId") : null);
            map.put("hasVideo", podcast != null && Boolean.TRUE.equals(podcast.get("hasVideo")));
            map.put("sentences", sentencesMap.getOrDefault(l.id(), List.of()));
            return map;
        }).toList();

        return Map.of("lessons", items, "total", items.size());
    }

    @GetMapping("/grammar/lessons/{id}/media")
    public ResponseEntity<?> getGrammarLessonMedia(@PathVariable String id) {
        var rows = jdbc.queryForList("""
            SELECT g.id AS lesson_id, g.podcast_id, p.audio_cache_key, p.duration_seconds,
                   p.script AS podcast_timings, v.video_url
            FROM grammar_lessons g
            LEFT JOIN podcasts p ON p.id = g.podcast_id AND p.status = 'ready'
            LEFT JOIN podcast_videos v ON v.podcast_id = p.id AND v.status = 'done'
            WHERE g.id = ?
            """, id);
        if (rows.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Lesson not found"));
        }

        var row = rows.get(0);
        String audioKey = (String) row.get("audio_cache_key");
        // Only expose the podcast when it is ready and has audio
        boolean hasPodcast = audioKey != null;

        var media = new java.util.LinkedHashMap<String, Object>();
        media.put("lessonId", row.get("lesson_id"));
        media.put("podcastId", hasPodcast && row.get("podcast_id") != null ? row.get("podcast_id").toString() : null);
        media.put("podcastUrl", hasPodcast ? PODCAST_BASE_URL + audioKey : null);
        media.put("durationSeconds", hasPodcast ? row.get("duration_seconds") : null);
        Object videoUrl = hasPodcast ? row.get("video_url") : null;
        media.put("videoUrl", videoUrl);
        media.put("videoOffsetMs", videoUrl != null ? VIDEO_INTRO_OFFSET_MS : null);
        media.put("subtitles", hasPodcast ? toLessonSubtitles(row.get("podcast_timings")) : List.of());
        return ResponseEntity.ok(media);
    }

    // Podcast info (URL, duration, video flag) for every lesson with a ready podcast, in one query.
    private Map<String, Map<String, Object>> loadPodcastInfo() {
        Map<String, Map<String, Object>> podcastMap = new java.util.LinkedHashMap<>();
        try {
            var rows = jdbc.queryForList(
                "SELECT g.id AS lesson_id, g.podcast_id, p.audio_cache_key, p.duration_seconds, p.title AS podcast_title, " +
                "(v.podcast_id IS NOT NULL) AS has_video " +
                "FROM grammar_lessons g JOIN podcasts p ON p.id = g.podcast_id " +
                "LEFT JOIN podcast_videos v ON v.podcast_id = g.podcast_id AND v.status = 'done' AND v.video_url IS NOT NULL " +
                "WHERE p.status = 'ready'");
            for (var row : rows) {
                String lessonId = (String) row.get("lesson_id");
                String audioKey = (String) row.get("audio_cache_key");
                if (audioKey != null) {
                    var podcast = new java.util.HashMap<String, Object>();
                    podcast.put("podcastId", row.get("podcast_id") != null ? row.get("podcast_id").toString() : null);
                    podcast.put("podcastUrl", PODCAST_BASE_URL + audioKey);
                    podcast.put("durationSeconds", row.get("duration_seconds") != null ? row.get("duration_seconds") : 0);
                    podcast.put("hasVideo", Boolean.TRUE.equals(row.get("has_video")));
                    podcastMap.put(lessonId, podcast);
                }
            }
        } catch (Exception ignored) {}
        return podcastMap;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toLessonSubtitles(Object timings) {
        if (timings == null) return List.of();
        List<Map<String, Object>> raw;
        try {
            raw = OBJECT_MAPPER.readValue(timings.toString(), List.class);
        } catch (Exception e) {
            return List.of();
        }
        return raw.stream()
            .filter(t -> t != null && t.get("text") != null && t.get("start_ms") instanceof Number
                && t.get("end_ms") instanceof Number)
            .<Map<String, Object>>map(t -> {
                var s = new java.util.LinkedHashMap<String, Object>();
                s.put("text", t.get("text").toString().replace("<<", "").replace(">>", ""));
                s.put("language", t.get("language"));
                s.put("startMs", ((Number) t.get("start_ms")).longValue());
                s.put("endMs", ((Number) t.get("end_ms")).longValue());
                return s;
            })
            .toList();
    }

    @GetMapping("/review/due")
    public List<Map<String, Object>> getDueWords(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "10") int limit) {
        long chatId = getUserId(request);
        return sentWordRepo.getWordsDueForReview(chatId, Math.min(limit, 50));
    }

    @PostMapping("/review/recall")
    public ResponseEntity<?> submitRecall(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String wordValue = (String) body.get("wordValue");
        Object qualityObj = body.get("quality");

        if (wordValue == null || qualityObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordValue and quality (0-5) are required"));
        }

        int quality;
        try {
            quality = ((Number) qualityObj).intValue();
        } catch (ClassCastException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordValue and quality (0-5) are required"));
        }

        if (quality < 0 || quality > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordValue and quality (0-5) are required"));
        }

        sentWordRepo.updateSm2(chatId, wordValue, quality);
        return ResponseEntity.ok(Map.of("wordValue", wordValue, "quality", quality, "updated", true));
    }
}
