package io.flashcard.service;

import io.flashcard.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.*;

/**
 * Scheduled service that pre-generates pronunciation audio for words.
 * Stores WAV files in the podcast cache directory and saves the filename in words.audio_cache_key.
 * Runs every 10 minutes, processes up to 20 words per run.
 */
@Service
public class WordAudioService {

    private static final Logger log = LoggerFactory.getLogger(WordAudioService.class);
    private static final String JOB_NAME = "word-audio-generation";
    private static final int BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final NotificationService notifications;
    private final SchedulerHistoryService historyService;
    private final Path podcastDir;

    private volatile boolean running = false;

    public WordAudioService(JdbcTemplate jdbc, AppProperties appProperties, HttpClient httpClient,
                            ObjectMapper objectMapper, NotificationService notifications,
                            SchedulerHistoryService historyService,
                            @Value("${CACHE_DIR:/app/cache}") String cacheDir) {
        this.jdbc = jdbc;
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.notifications = notifications;
        this.historyService = historyService;
        this.podcastDir = Path.of(cacheDir, "word-audio");
        try { Files.createDirectories(podcastDir); } catch (Exception ignored) {}
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000) // 10 min, 2 min initial delay
    public void generateWordAudio() {
        if (running) return;
        running = true;
        long start = System.currentTimeMillis();
        int generated = 0, failed = 0;

        try {
            updateStatus("running");

            // Find words without audio
            var words = jdbc.queryForList(
                "SELECT id, estonian FROM words WHERE audio_cache_key IS NULL ORDER BY cefr_level, estonian LIMIT ?",
                BATCH_SIZE);

            if (words.isEmpty()) {
                updateStatus("idle");
                running = false;
                return;
            }

            log.info("[word-audio] Starting batch: {} words to process", words.size());

            for (var word : words) {
                String wordId = (String) word.get("id");
                String estonian = (String) word.get("estonian");

                try {
                    // Get a sentence for context
                    var sentences = jdbc.queryForList(
                        "SELECT estonian FROM word_sentences WHERE word_id = ? ORDER BY sort_order LIMIT 1", wordId);
                    String sentence = sentences.isEmpty() ? estonian : (String) sentences.get(0).get("estonian");

                    // Synthesize: word + pause + sentence
                    String text = sentence != null && !sentence.equals(estonian)
                        ? estonian + ". ... " + sentence
                        : estonian;

                    byte[] wavData = callTts(text);
                    if (wavData == null) {
                        failed++;
                        continue;
                    }

                    // Save to file
                    String filename = "word-" + wordId + ".wav";
                    Path audioPath = podcastDir.resolve(filename);
                    Files.write(audioPath, wavData);

                    // Update DB
                    jdbc.update("UPDATE words SET audio_cache_key = ? WHERE id = ?", filename, wordId);
                    generated++;
                    log.debug("[word-audio] Generated audio for \"{}\" ({}bytes)", estonian, wavData.length);

                } catch (Exception e) {
                    failed++;
                    log.warn("[word-audio] Failed for \"{}\": {}", estonian, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - start;
            log.info("[word-audio] Batch complete: {} generated, {} failed in {}ms", generated, failed, duration);
            recordSuccess(start, generated);

            int remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM words WHERE audio_cache_key IS NULL", Integer.class);
            String msg = generated + " words, " + failed + " failed (" + (duration / 1000) + "s). " + remaining + " remaining.";
            historyService.logRun(JOB_NAME, "success", java.time.Instant.ofEpochMilli(start), duration, generated, failed, msg);

            if (generated > 0) {
                notifications.info("words", "Word Audio Generated", msg);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[word-audio] Failed: {}", e.getMessage(), e);
            recordFailure(start);
            historyService.logRun(JOB_NAME, "failed", java.time.Instant.ofEpochMilli(start), duration, 0, 0, e.getMessage());
            notifications.error("words", "Word Audio Generation Failed", e.getMessage());
        } finally {
            running = false;
        }
    }

    /**
     * Manually trigger audio generation for a specific batch size.
     */
    public void triggerGeneration() {
        Thread.startVirtualThread(this::generateWordAudio);
    }

    public Map<String, Object> getStatus() {
        var rows = jdbc.queryForList("SELECT * FROM scheduler_status WHERE job_name = ?", JOB_NAME);
        int totalWords = jdbc.queryForObject("SELECT COUNT(*) FROM words", Integer.class);
        int withAudio = jdbc.queryForObject("SELECT COUNT(*) FROM words WHERE audio_cache_key IS NOT NULL", Integer.class);
        int withoutAudio = totalWords - withAudio;

        var result = new LinkedHashMap<String, Object>();
        if (!rows.isEmpty()) result.putAll(rows.get(0));
        result.put("totalWords", totalWords);
        result.put("withAudio", withAudio);
        result.put("withoutAudio", withoutAudio);
        result.put("coveragePercent", totalWords > 0 ? Math.round((withAudio * 100.0) / totalWords) : 0);
        return result;
    }

    private byte[] callTts(String text) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "text", text, "language", "et", "voice", "default"));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/v1/synthesize"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("[word-audio] TTS API returned {}", response.statusCode());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            log.warn("[word-audio] TTS call failed: {}", e.getMessage());
            return null;
        }
    }

    private void updateStatus(String status) {
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (job_name) DO UPDATE SET status = ?, last_run_at = NOW()
            """, JOB_NAME, status, status);
    }

    private void recordSuccess(long startMs, int count) {
        long duration = System.currentTimeMillis() - startMs;
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at, last_success_at, last_duration_ms, items_processed)
            VALUES (?, 'idle', NOW(), NOW(), ?, ?)
            ON CONFLICT (job_name) DO UPDATE SET status = 'idle', last_run_at = NOW(),
                last_success_at = NOW(), last_duration_ms = ?,
                items_processed = scheduler_status.items_processed + ?
            """, JOB_NAME, duration, count, duration, count);
    }

    private void recordFailure(long startMs) {
        long duration = System.currentTimeMillis() - startMs;
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at, last_duration_ms, items_failed)
            VALUES (?, 'idle', NOW(), ?, 1)
            ON CONFLICT (job_name) DO UPDATE SET status = 'idle', last_run_at = NOW(),
                last_duration_ms = ?,
                items_failed = scheduler_status.items_failed + 1
            """, JOB_NAME, duration, duration);
    }
}
