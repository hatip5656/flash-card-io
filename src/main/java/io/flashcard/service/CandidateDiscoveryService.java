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
    private static final int TARGET_CANDIDATES = 30; // total per run
    private static final int MAX_PATTERNS_PER_RUN = 15;
    private static final int LOOKUPS_PER_PATTERN = 15;

    private final EkilexService ekilexService;
    private final TranslationService translationService;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbc;
    private final WordBankService wordBankService;
    private final NotificationService notifications;
    private final SchedulerHistoryService historyService;

    private volatile boolean running = false;

    public CandidateDiscoveryService(EkilexService ekilexService, TranslationService translationService,
                                     AppProperties appProperties, JdbcTemplate jdbc,
                                     WordBankService wordBankService, NotificationService notifications,
                                     SchedulerHistoryService historyService) {
        this.ekilexService = ekilexService;
        this.translationService = translationService;
        this.appProperties = appProperties;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.historyService = historyService;
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
            // Build combined exclusion set — include ALL candidates (even rejected, no point re-discovering)
            Set<String> excludeWords = new HashSet<>(
                jdbc.queryForList("SELECT estonian FROM words", String.class));
            excludeWords.addAll(
                jdbc.queryForList("SELECT estonian FROM candidate_words", String.class));

            Set<Integer> checkedIds = new HashSet<>(
                jdbc.queryForList("SELECT word_id FROM ekilex_checked_words", Integer.class));
            log.info("[candidate-discovery] Exclusion set: {} words, {} checked Ekilex IDs", excludeWords.size(), checkedIds.size());

            int patternBase = getNextPatternIndex();
            int patternCount = ekilexService.getPatternCount();

            // Phase 1: Batch search — find ALL words with English translations (with or without CEFR)
            List<EkilexService.EkilexWord> foundWords = new ArrayList<>();
            int patternsSearched = 0;

            for (int p = 0; p < MAX_PATTERNS_PER_RUN && foundWords.size() < TARGET_CANDIDATES; p++) {
                int patternIdx = (patternBase + p) % patternCount;
                String pattern = ekilexService.getPattern(patternIdx);
                patternsSearched++;

                try {
                    List<EkilexService.CheckedWord> checkedOut = new ArrayList<>();
                    var words = ekilexService.batchSearchWords(excludeWords, apiKey, pattern, checkedIds,
                        LOOKUPS_PER_PATTERN, checkedOut);

                    // Cache all checked word IDs
                    for (var checked : checkedOut) {
                        checkedIds.add(checked.wordId());
                        try {
                            jdbc.update("""
                                INSERT INTO ekilex_checked_words (word_id, word_value, has_cefr)
                                VALUES (?, ?, ?) ON CONFLICT (word_id) DO NOTHING
                                """, checked.wordId(), checked.wordValue(), checked.hasCefr());
                        } catch (Exception ignored) {}
                    }

                    for (var word : words) {
                        if (!excludeWords.contains(word.wordValue().toLowerCase())) {
                            foundWords.add(word);
                            excludeWords.add(word.wordValue().toLowerCase());
                        }
                    }
                } catch (Exception e) {
                    log.debug("[candidate-discovery] Error with pattern {}: {}", pattern, e.getMessage());
                }
            }

            log.info("[candidate-discovery] Phase 1: found {} words across {} patterns", foundWords.size(), patternsSearched);

            // Phase 2: Batch translate English → Turkish
            List<String> englishTexts = foundWords.stream().map(EkilexService.EkilexWord::english).toList();
            List<String> turkishTranslations = null;
            if (!englishTexts.isEmpty()) {
                try {
                    turkishTranslations = translationService.translateBatch(englishTexts, "en", "tr");
                    if (turkishTranslations != null) {
                        log.info("[candidate-discovery] Translated {} words en→tr", turkishTranslations.size());
                    }
                } catch (Exception e) {
                    log.warn("[candidate-discovery] Batch translation failed: {}", e.getMessage());
                }
            }

            // Phase 3: Insert all candidates with translations
            for (int i = 0; i < foundWords.size(); i++) {
                var word = foundWords.get(i);
                String estonian = word.wordValue().toLowerCase();
                String turkish = (turkishTranslations != null && i < turkishTranslations.size())
                    ? turkishTranslations.get(i) : null;

                try {
                    int inserted = jdbc.update("""
                        INSERT INTO candidate_words (estonian, english, turkish, cefr_level, status)
                        VALUES (?, ?, ?, ?, 'pending')
                        ON CONFLICT (estonian) DO NOTHING
                        """, estonian, word.english(), turkish, word.cefrLevel());

                    if (inserted == 0) {
                        totalSkipped++;
                        continue; // already exists, skip sentences
                    }

                    // Save example sentences
                    if (word.usages() != null && !word.usages().isEmpty()) {
                        Integer candidateId = jdbc.queryForObject(
                            "SELECT id FROM candidate_words WHERE estonian = ?", Integer.class, estonian);
                        if (candidateId != null) {
                            // Batch translate sentences too
                            List<String> sentenceTexts = word.usages().stream()
                                .map(EkilexService.Usage::english)
                                .filter(e -> e != null && !e.isBlank())
                                .toList();
                            List<String> sentenceTr = null;
                            if (!sentenceTexts.isEmpty()) {
                                try {
                                    sentenceTr = translationService.translateBatch(sentenceTexts, "en", "tr");
                                } catch (Exception ignored) {}
                            }

                            int sortOrder = 0;
                            int trIdx = 0;
                            for (var usage : word.usages()) {
                                String sTurkish = null;
                                if (usage.english() != null && !usage.english().isBlank()
                                    && sentenceTr != null && trIdx < sentenceTr.size()) {
                                    sTurkish = sentenceTr.get(trIdx++);
                                }
                                jdbc.update("""
                                    INSERT INTO candidate_sentences (candidate_id, estonian, english, turkish, sort_order)
                                    VALUES (?, ?, ?, ?, ?)
                                    """, candidateId, usage.estonian(), usage.english(), sTurkish, sortOrder++);
                            }
                        }
                    }
                    totalAdded++;
                } catch (Exception e) {
                    log.debug("[candidate-discovery] Error inserting {}: {}", estonian, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - start;
            log.info("[candidate-discovery] Complete: {} added, {} skipped in {}ms", totalAdded, totalSkipped, duration);
            recordSuccess(start, totalAdded);
            String msg = totalAdded + " new candidates discovered, " + totalSkipped + " skipped (" + (duration / 1000) + "s)";
            historyService.logRun(JOB_NAME, "success", java.time.Instant.ofEpochMilli(start), duration, totalAdded, 0, msg);
            if (totalAdded > 0) {
                notifications.success("candidates", "Candidate Discovery Complete", msg);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[candidate-discovery] Failed: {}", e.getMessage(), e);
            recordFailure(start);
            historyService.logRun(JOB_NAME, "failed", java.time.Instant.ofEpochMilli(start), duration, 0, 0, e.getMessage());
            notifications.error("candidates", "Candidate Discovery Failed", e.getMessage());
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
