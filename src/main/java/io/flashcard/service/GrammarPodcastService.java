package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import io.flashcard.model.GrammarLesson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GrammarPodcastService {

    private static final Logger log = LoggerFactory.getLogger(GrammarPodcastService.class);
    private static final String JOB_NAME = "grammar-podcast";

    private static final String GRAMMAR_PROMPT = """
            Convert this grammar lesson into a bilingual podcast script for Estonian learners.
            The learner's native language is %s.

            TOPIC: %s
            CONTENT: %s

            Generate a JSON array of segments. Each segment: {"text": "...", "language": "%s" or "et", "pause_after_ms": int}

            STRUCTURE:
            1. Introduce the grammar topic (native lang)
            2. Explain the rule simply (native lang, one-two sentences)
            3. Give three Estonian example sentences showing the rule
            4. Translate each example (native lang)
            5. Pause two thousand ms after each Estonian sentence
            6. Short practice prompt (native lang)
            7. Brief closing

            RULES:
            - ALL text must be speakable — write numbers as words, no codes or symbols
            - Estonian sentences: max twelve words, spell out numbers
            - In non-Estonian segments, wrap Estonian words with <<>> markers
            - NEVER put <<>> in Estonian segments
            - Keep under fifteen segments
            - Return ONLY valid JSON array, no markdown
            - MUST end with ]
            """;

    private final JdbcTemplate jdbc;
    private final GeminiService geminiService;
    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final NotificationService notifications;
    private final SchedulerHistoryService historyService;
    private final Path podcastDir;

    private volatile boolean running = false;

    public GrammarPodcastService(JdbcTemplate jdbc, GeminiService geminiService,
                                 AppProperties appProperties, HttpClient httpClient,
                                 ObjectMapper objectMapper, NotificationService notifications,
                                 SchedulerHistoryService historyService,
                                 @org.springframework.beans.factory.annotation.Value("${CACHE_DIR:/app/cache}") String cacheDir) {
        this.jdbc = jdbc;
        this.geminiService = geminiService;
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.notifications = notifications;
        this.historyService = historyService;
        this.podcastDir = Path.of(cacheDir, "podcasts");
        try { Files.createDirectories(podcastDir); } catch (Exception ignored) {}
    }

    /**
     * Scheduled job: generates ONE grammar podcast every 30 minutes.
     * Uses pre-written scripts from podcast_script column — no Gemini needed.
     */
    @Scheduled(fixedDelay = 1800000, initialDelay = 60000) // 30 min, 1 min initial delay
    public void generateNextGrammarPodcast() {
        if (running) {
            log.debug("[grammar-podcast] Already running, skipping");
            return;
        }

        // Find next grammar lesson with a pre-written script but no podcast yet
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, cefr_level, topic, podcast_script
            FROM grammar_lessons WHERE podcast_id IS NULL AND podcast_script IS NOT NULL
            ORDER BY cefr_level, id LIMIT 1
            """);

        if (rows.isEmpty()) {
            log.debug("[grammar-podcast] No lessons with pre-written scripts pending");
            return;
        }

        running = true;
        long start = System.currentTimeMillis();
        updateStatus("running");

        try {
            var lesson = rows.get(0);
            String lessonId = (String) lesson.get("id");
            String level = (String) lesson.get("cefr_level");
            String topic = (String) lesson.get("topic");
            String scriptJson = lesson.get("podcast_script").toString();

            log.info("[grammar-podcast] Generating from pre-written script for lesson {} ({}): {}", lessonId, level, topic);

            List<?> segments = objectMapper.readValue(scriptJson, List.class);
            if (segments.isEmpty()) {
                log.warn("[grammar-podcast] Empty segments for lesson {}", lessonId);
                recordFailure(start);
                return;
            }

            // Call TTS
            Map<String, Object> ttsRequest = Map.of("segments", segments, "output_sample_rate", 24000);
            String body = objectMapper.writeValueAsString(ttsRequest);

            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/v1/podcast/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<byte[]> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("[grammar-podcast] TTS returned {} for lesson {}", response.statusCode(), lessonId);
                recordFailure(start);
                return;
            }

            // Save audio
            String podcastId = UUID.randomUUID().toString();
            String filename = podcastId + ".wav";
            Path audioPath = podcastDir.resolve(filename);
            Files.write(audioPath, response.body());

            // Parse timings
            String timingsJson = "[]";
            String timingsHeader = response.headers().firstValue("X-Segment-Timings").orElse(null);
            if (timingsHeader != null) {
                timingsJson = timingsHeader;
            }

            // Estimate duration
            byte[] wavData = response.body();
            int durationSeconds = 0;
            if (wavData.length >= 44) {
                int sr = (wavData[24] & 0xFF) | ((wavData[25] & 0xFF) << 8) | ((wavData[26] & 0xFF) << 16) | ((wavData[27] & 0xFF) << 24);
                int ds = (wavData[40] & 0xFF) | ((wavData[41] & 0xFF) << 8) | ((wavData[42] & 0xFF) << 16) | ((wavData[43] & 0xFF) << 24);
                if (sr > 0) durationSeconds = ds / (sr * 2);
            }

            // Create podcast record
            String title = "Grammar: " + topic;
            jdbc.update("""
                INSERT INTO podcasts (id, chat_id, title, description, script, audio_cache_key,
                    duration_seconds, cefr_level, status, created_at)
                VALUES (?, 0, ?, ?, ?::jsonb, ?, ?, ?, 'ready', NOW())
                """, podcastId, title, "Grammar lesson podcast",
                timingsJson, filename, durationSeconds, level);

            // Link to grammar lesson
            jdbc.update("UPDATE grammar_lessons SET podcast_id = ? WHERE id = ?", podcastId, lessonId);

            long duration = System.currentTimeMillis() - start;
            log.info("[grammar-podcast] Generated podcast {} for lesson {} in {}ms ({}s audio)",
                podcastId, lessonId, duration, durationSeconds);

            recordSuccess(start);
            String msg = "Lesson " + lessonId + " — " + durationSeconds + "s audio (" + (duration / 1000) + "s generation)";
            historyService.logRun("grammar-podcast", "success", java.time.Instant.ofEpochMilli(start), duration, 1, 0, msg);
            notifications.success("podcasts", "Grammar Podcast Generated", msg);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[grammar-podcast] Failed: {}", e.getMessage(), e);
            recordFailure(start);
            historyService.logRun("grammar-podcast", "failed", java.time.Instant.ofEpochMilli(start), duration, 0, 1, e.getMessage());
            notifications.error("podcasts", "Grammar Podcast Failed", e.getMessage());
        } finally {
            running = false;
        }
    }

    private void updateStatus(String status) {
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (job_name) DO UPDATE SET status = ?, last_run_at = NOW()
            """, JOB_NAME, status, status);
    }

    private void recordSuccess(long startMs) {
        long duration = System.currentTimeMillis() - startMs;
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at, last_success_at, last_duration_ms, items_processed)
            VALUES (?, 'idle', NOW(), NOW(), ?, 1)
            ON CONFLICT (job_name) DO UPDATE SET
                status = 'idle', last_success_at = NOW(), last_duration_ms = ?,
                items_processed = scheduler_status.items_processed + 1
            """, JOB_NAME, duration, duration);
    }

    private void recordFailure(long startMs) {
        long duration = System.currentTimeMillis() - startMs;
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at, last_duration_ms, items_failed)
            VALUES (?, 'idle', NOW(), ?, 1)
            ON CONFLICT (job_name) DO UPDATE SET
                status = 'idle', last_duration_ms = ?,
                items_failed = scheduler_status.items_failed + 1
            """, JOB_NAME, duration, duration);
    }

    /**
     * Get scheduler status for admin UI.
     */
    public Map<String, Object> getStatus() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM scheduler_status WHERE job_name = ?", JOB_NAME);

        int pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM grammar_lessons WHERE podcast_id IS NULL",
            Integer.class);
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM grammar_lessons", Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            result.putAll(rows.get(0));
        }
        result.put("pendingLessons", pending);
        result.put("totalLessons", total);
        result.put("completedLessons", total - pending);
        return result;
    }

    /**
     * Manually trigger generation for a specific lesson.
     */
    public void triggerManual(String lessonId) {
        jdbc.update("UPDATE grammar_lessons SET podcast_id = NULL WHERE id = ?", lessonId);
        log.info("[grammar-podcast] Manual trigger for lesson {}", lessonId);
    }

}
