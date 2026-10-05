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
 * Runs every 5 hours. Uses systematic two-letter prefix crawling with pattern
 * rotation to explore the full Ekilex vocabulary over time.
 */
@Service
public class CandidateDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(CandidateDiscoveryService.class);
    private static final String JOB_NAME = "candidate-discovery";
    private static final String[] LEVELS = {"A1", "A2", "B1", "B2"};
    private static final int WORDS_PER_LEVEL = 8;
    private static final int MAX_ATTEMPTS_PER_LEVEL = 30;

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

    /** Get the next pattern index, rotating through all patterns across runs */
    private int getNextPatternIndex() {
        try {
            Integer idx = jdbc.queryForObject(
                "SELECT COALESCE((items_processed + items_failed), 0) FROM scheduler_status WHERE job_name = ?",
                Integer.class, JOB_NAME);
            return idx != null ? idx : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Runs every 5 hours. Uses systematic pattern rotation to discover new words.
     * Each run tries multiple different two-letter prefixes to maximize coverage.
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
            // Build combined exclusion set — words + non-rejected candidates
            Set<String> excludeWords = new HashSet<>(
                jdbc.queryForList("SELECT estonian FROM words", String.class));
            excludeWords.addAll(
                jdbc.queryForList("SELECT estonian FROM candidate_words WHERE status != 'rejected'", String.class));

            // Load already-checked Ekilex word IDs (no CEFR) to skip them
            Set<Integer> checkedIds = new HashSet<>(
                jdbc.queryForList("SELECT word_id FROM ekilex_checked_words", Integer.class));
            log.info("[candidate-discovery] Exclusion set: {} words, {} checked Ekilex IDs", excludeWords.size(), checkedIds.size());

            int patternBase = getNextPatternIndex();
            int patternCount = ekilexService.getPatternCount();

            for (String level : LEVELS) {
                int added = 0;
                int attempts = 0;
                while (added < WORDS_PER_LEVEL && attempts < MAX_ATTEMPTS_PER_LEVEL) {
                    int patternIdx = (patternBase + attempts * LEVELS.length + Arrays.asList(LEVELS).indexOf(level)) % patternCount;
                    String pattern = ekilexService.getPattern(patternIdx);
                    attempts++;

                    try {
                        var result = ekilexService.searchForLevel(level, excludeWords, apiKey, pattern, checkedIds);

                        // Cache all checked words (CEFR or not) to avoid re-checking
                        for (var checked : result.checked()) {
                            checkedIds.add(checked.wordId());
                            try {
                                jdbc.update("""
                                    INSERT INTO ekilex_checked_words (word_id, word_value, has_cefr)
                                    VALUES (?, ?, ?)
                                    ON CONFLICT (word_id) DO NOTHING
                                    """, checked.wordId(), checked.wordValue(), checked.hasCefr());
                            } catch (Exception ignored) {}
                        }

                        var word = result.word();
                        if (word == null || word.english() == null) continue;

                        String estonian = word.wordValue().toLowerCase();
                        if (excludeWords.contains(estonian)) {
                            totalSkipped++;
                            continue;
                        }

                        // Insert candidate word
                        jdbc.update("""
                            INSERT INTO candidate_words (estonian, english, cefr_level, status)
                            VALUES (?, ?, ?, 'pending')
                            ON CONFLICT (estonian) DO NOTHING
                            """, estonian, word.english(), level);

                        // Save example sentences from Ekilex
                        if (word.usages() != null && !word.usages().isEmpty()) {
                            Integer candidateId = jdbc.queryForObject(
                                "SELECT id FROM candidate_words WHERE estonian = ?", Integer.class, estonian);
                            if (candidateId != null) {
                                int sortOrder = 0;
                                for (var usage : word.usages()) {
                                    jdbc.update("""
                                        INSERT INTO candidate_sentences (candidate_id, estonian, english, sort_order)
                                        VALUES (?, ?, ?, ?)
                                        """, candidateId, usage.estonian(), usage.english(), sortOrder++);
                                }
                            }
                        }

                        excludeWords.add(estonian);
                        added++;
                        totalAdded++;
                    } catch (Exception e) {
                        log.debug("[candidate-discovery] Error with pattern {} for {}: {}", pattern, level, e.getMessage());
                    }
                }
                log.info("[candidate-discovery] {} level: {} new candidates ({} patterns tried)", level, added, attempts);
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
        int pendingCandidates = jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_words WHERE status = 'pending'", Integer.class);
        int totalCandidates = jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_words", Integer.class);
        int withSentences = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT candidate_id) FROM candidate_sentences", Integer.class);
        int checkedCache = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ekilex_checked_words", Integer.class);
        int checkedWithCefr = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ekilex_checked_words WHERE has_cefr = true", Integer.class);

        Map<String, Object> result = new LinkedHashMap<>();
        if (!rows.isEmpty()) result.putAll(rows.get(0));
        result.put("totalWords", totalWords);
        result.put("pendingCandidates", pendingCandidates);
        result.put("totalCandidates", totalCandidates);
        result.put("candidatesWithSentences", withSentences);
        result.put("totalPatterns", ekilexService.getPatternCount());
        result.put("ekilexCheckedWords", checkedCache);
        result.put("ekilexWithCefr", checkedWithCefr);
        return result;
    }
}
