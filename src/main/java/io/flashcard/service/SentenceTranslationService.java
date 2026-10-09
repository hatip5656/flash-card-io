package io.flashcard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scheduled service that translates word_sentences missing Turkish translations.
 * Uses batch translation (English → Turkish) for efficiency.
 * Runs every 15 minutes, processes up to 50 sentences per run.
 */
@Service
public class SentenceTranslationService {

    private static final Logger log = LoggerFactory.getLogger(SentenceTranslationService.class);
    private static final String JOB_NAME = "sentence-translation";
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final TranslationService translationService;
    private final NotificationService notifications;
    private final SchedulerHistoryService historyService;

    private volatile boolean running = false;

    public SentenceTranslationService(JdbcTemplate jdbc, TranslationService translationService,
                                       NotificationService notifications, SchedulerHistoryService historyService) {
        this.jdbc = jdbc;
        this.translationService = translationService;
        this.notifications = notifications;
        this.historyService = historyService;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000) // 15 min, 1 min initial delay
    public void translateSentences() {
        if (running) return;
        running = true;
        long start = System.currentTimeMillis();
        int translated = 0, failed = 0;

        try {
            updateStatus("running");

            // Find sentences missing Turkish that have English
            var sentences = jdbc.queryForList("""
                SELECT ws.id, ws.english
                FROM word_sentences ws
                WHERE (ws.turkish IS NULL OR ws.turkish = '')
                  AND ws.english IS NOT NULL AND ws.english != ''
                ORDER BY ws.id
                LIMIT ?
                """, BATCH_SIZE);

            if (sentences.isEmpty()) {
                updateStatus("idle");
                running = false;
                return;
            }

            log.info("[sentence-translation] Starting batch: {} sentences to translate", sentences.size());

            // Collect English texts for batch translation
            List<Integer> ids = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (var s : sentences) {
                ids.add((Integer) s.get("id"));
                texts.add((String) s.get("english"));
            }

            // Translate in sub-batches of 32 (API limit)
            for (int i = 0; i < texts.size(); i += 32) {
                int end = Math.min(i + 32, texts.size());
                List<String> batch = texts.subList(i, end);
                List<Integer> batchIds = ids.subList(i, end);

                List<String> results = translationService.translateBatch(batch, "en", "tr");
                if (results != null && results.size() == batch.size()) {
                    for (int j = 0; j < results.size(); j++) {
                        String tr = results.get(j);
                        if (tr != null && !tr.isBlank()) {
                            try {
                                jdbc.update("UPDATE word_sentences SET turkish = ? WHERE id = ?", tr, batchIds.get(j));
                                translated++;
                            } catch (Exception e) {
                                failed++;
                                log.warn("[sentence-translation] Failed to update id {}: {}", batchIds.get(j), e.getMessage());
                            }
                        } else {
                            failed++;
                        }
                    }
                } else {
                    // Batch failed, try one-by-one
                    for (int j = 0; j < batch.size(); j++) {
                        try {
                            String tr = translationService.translate(batch.get(j), "en", "tr");
                            if (tr != null && !tr.isBlank()) {
                                jdbc.update("UPDATE word_sentences SET turkish = ? WHERE id = ?", tr, batchIds.get(j));
                                translated++;
                            } else {
                                failed++;
                            }
                        } catch (Exception e) {
                            failed++;
                        }
                    }
                }
            }

            long duration = System.currentTimeMillis() - start;
            log.info("[sentence-translation] Batch complete: {} translated, {} failed in {}ms", translated, failed, duration);
            recordSuccess(start, translated);

            int remaining = jdbc.queryForObject("""
                SELECT COUNT(*) FROM word_sentences
                WHERE (turkish IS NULL OR turkish = '')
                  AND english IS NOT NULL AND english != ''
                """, Integer.class);

            String msg = translated + " sentences translated, " + failed + " failed (" + (duration / 1000) + "s). " + remaining + " remaining.";
            historyService.logRun(JOB_NAME, "success", java.time.Instant.ofEpochMilli(start), duration, translated, failed, msg);

            if (translated > 0) {
                notifications.info("words", "Sentences Translated", msg);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[sentence-translation] Failed: {}", e.getMessage(), e);
            recordFailure(start);
            historyService.logRun(JOB_NAME, "failed", java.time.Instant.ofEpochMilli(start), duration, 0, 0, e.getMessage());
            notifications.error("words", "Sentence Translation Failed", e.getMessage());
        } finally {
            running = false;
        }
    }

    public void triggerTranslation() {
        Thread.startVirtualThread(this::translateSentences);
    }

    public Map<String, Object> getStatus() {
        var rows = jdbc.queryForList("SELECT * FROM scheduler_status WHERE job_name = ?", JOB_NAME);
        int totalSentences = jdbc.queryForObject("SELECT COUNT(*) FROM word_sentences", Integer.class);
        int withTurkish = jdbc.queryForObject(
            "SELECT COUNT(*) FROM word_sentences WHERE turkish IS NOT NULL AND turkish != ''", Integer.class);
        int withoutTurkish = jdbc.queryForObject("""
            SELECT COUNT(*) FROM word_sentences
            WHERE (turkish IS NULL OR turkish = '')
              AND english IS NOT NULL AND english != ''
            """, Integer.class);

        var result = new LinkedHashMap<String, Object>();
        if (!rows.isEmpty()) result.putAll(rows.get(0));
        result.put("totalSentences", totalSentences);
        result.put("withTurkish", withTurkish);
        result.put("withoutTurkish", withoutTurkish);
        result.put("coveragePercent", totalSentences > 0 ? Math.round((withTurkish * 100.0) / totalSentences) : 0);
        return result;
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
