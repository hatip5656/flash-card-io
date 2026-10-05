package io.flashcard.service;

import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scheduled service that discovers new candidate words from Ekilex API.
 * Runs every 5 hours, fetches random words for each CEFR level.
 */
@Service
public class CandidateDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CandidateDiscoveryService.class);
    private static final String JOB_NAME = "candidate-discovery";
    private static final String[] LEVELS = {"A1", "A2", "B1", "B2"};
    private static final int WORDS_PER_LEVEL = 5;

    private final EkilexService ekilexService;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbc;
    private final WordBankService wordBankService;

    private volatile boolean running = false;

    public CandidateDiscoveryService(EkilexService ekilexService, AppProperties appProperties,
                                     JdbcTemplate jdbc, WordBankService wordBankService) {
        this.ekilexService = ekilexService;
        this.appProperties = appProperties;
        this.jdbc = jdbc;
        this.wordBankService = wordBankService;
    }

    /**
     * Runs every 5 hours. Discovers new words from Ekilex and adds as candidates.
     */
    @Scheduled(fixedDelay = 18000000, initialDelay = 300000) // 5h, 5min initial delay
    public void discoverCandidates() {
        String apiKey = appProperties.getEkilexApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[candidate-discovery] Ekilex API key not configured, skipping");
            return;
        }
        if (running) {
            log.debug("[candidate-discovery] Already running, skipping");
            return;
        }

        running = true;
        long start = System.currentTimeMillis();
        updateStatus("running");

        int totalAdded = 0;
        int totalSkipped = 0;

        try {
            Set<String> existingWords = new HashSet<>(
                jdbc.queryForList("SELECT estonian FROM words", String.class));
            Set<String> existingCandidates = new HashSet<>(
                jdbc.queryForList("SELECT estonian FROM candidate_words WHERE status != 'rejected'", String.class));

            for (String level : LEVELS) {
                int added = 0;
                for (int attempt = 0; attempt < WORDS_PER_LEVEL * 3 && added < WORDS_PER_LEVEL; attempt++) {
                    try {
                        var word = ekilexService.getRandomWordForLevel(level, existingWords, apiKey);
                        if (word == null || word.english() == null) continue;

                        String estonian = word.wordValue().toLowerCase();
                        if (existingWords.contains(estonian) || existingCandidates.contains(estonian)) {
                            totalSkipped++;
                            continue;
                        }

                        // Insert as candidate
                        jdbc.update("""
                            INSERT INTO candidate_words (estonian, english, cefr_level, status)
                            VALUES (?, ?, ?, 'pending')
                            ON CONFLICT (estonian) DO NOTHING
                            """, estonian, word.english(), level);

                        existingCandidates.add(estonian);
                        added++;
                        totalAdded++;
                    } catch (Exception e) {
                        log.debug("[candidate-discovery] Error fetching word for {}: {}", level, e.getMessage());
                    }
                }
                log.info("[candidate-discovery] {} level: {} new candidates", level, added);
            }

            long duration = System.currentTimeMillis() - start;
            log.info("[candidate-discovery] Complete: {} added, {} skipped in {}ms", totalAdded, totalSkipped, duration);
            recordSuccess(start, totalAdded);

        } catch (Exception e) {
            log.error("[candidate-discovery] Failed: {}", e.getMessage(), e);
            recordFailure(start);
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

    private void recordSuccess(long startMs, int added) {
        long duration = System.currentTimeMillis() - startMs;
        jdbc.update("""
            INSERT INTO scheduler_status (job_name, status, last_run_at, last_success_at, last_duration_ms, items_processed)
            VALUES (?, 'idle', NOW(), NOW(), ?, ?)
            ON CONFLICT (job_name) DO UPDATE SET
                status = 'idle', last_success_at = NOW(), last_duration_ms = ?,
                items_processed = scheduler_status.items_processed + ?
            """, JOB_NAME, duration, added, duration, added);
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

    public Map<String, Object> getStatus() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM scheduler_status WHERE job_name = ?", JOB_NAME);

        int totalWords = jdbc.queryForObject("SELECT COUNT(*) FROM words", Integer.class);
        int totalCandidates = jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_words WHERE status = 'pending'", Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        if (!rows.isEmpty()) result.putAll(rows.get(0));
        result.put("totalWords", totalWords);
        result.put("pendingCandidates", totalCandidates);
        return result;
    }
}
