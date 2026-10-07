package io.flashcard.service;

import io.flashcard.repository.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepo;
    private final TranslationService translationService;
    private final NotificationService notificationService;
    private final WordBankService wordBankService;
    private final JdbcTemplate jdbc;

    public CandidateService(CandidateRepository candidateRepo, TranslationService translationService,
                            NotificationService notificationService, WordBankService wordBankService,
                            JdbcTemplate jdbc) {
        this.candidateRepo = candidateRepo;
        this.translationService = translationService;
        this.notificationService = notificationService;
        this.wordBankService = wordBankService;
        this.jdbc = jdbc;
    }

    // ─── Single candidate translation ────────────────────

    public Map<String, Object> autoTranslate(int id) {
        var rows = jdbc.queryForList("SELECT id, estonian, english, turkish FROM candidate_words WHERE id = ?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Candidate not found");

        String english = (String) rows.get(0).get("english");
        if (english == null || english.isBlank()) throw new IllegalArgumentException("No English translation to translate from");

        String turkish = translationService.translate(english, "en", "tr");
        if (turkish == null) throw new IllegalStateException("Translation service failed");

        // Translate sentences
        var sentenceRows = jdbc.queryForList(
            "SELECT id, english FROM candidate_sentences WHERE candidate_id = ? AND (turkish IS NULL OR turkish = '') AND english IS NOT NULL AND english != '' ORDER BY sort_order", id);
        int sentencesUpdated = 0;
        for (var s : sentenceRows) {
            String sentTr = translationService.translate((String) s.get("english"), "en", "tr");
            if (sentTr != null) {
                jdbc.update("UPDATE candidate_sentences SET turkish = ? WHERE id = ?", sentTr, s.get("id"));
                sentencesUpdated++;
            }
        }

        // Only mark as 'translated' if all sentences also have Turkish
        Integer missingTurkish = jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_sentences WHERE candidate_id = ? AND (turkish IS NULL OR turkish = '')", Integer.class, id);
        String newStatus = (missingTurkish == null || missingTurkish == 0) ? "translated" : "pending";

        jdbc.update("UPDATE candidate_words SET turkish = ?, status = ?, translated_at = NOW() WHERE id = ?", turkish, newStatus, id);
        return Map.of("turkish", turkish, "sentencesTranslated", sentencesUpdated, "status", newStatus);
    }

    // ─── Sentence management ─────────────────────────────

    public Map<String, Object> addSentence(int candidateId, String estonian, String english, String turkish) {
        var rows = jdbc.queryForList("SELECT id FROM candidate_words WHERE id = ?", candidateId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Candidate not found");

        if (isBlank(estonian) && isBlank(english) && isBlank(turkish)) {
            throw new IllegalArgumentException("Provide at least one sentence in any language");
        }

        // Auto-translate missing languages from whichever is provided
        if (!isBlank(estonian)) {
            if (isBlank(english)) english = translationService.translate(estonian, "et", "en");
            if (isBlank(turkish)) turkish = translationService.translate(estonian, "et", "tr");
        } else if (!isBlank(english)) {
            if (isBlank(estonian)) estonian = translationService.translate(english, "en", "et");
            if (isBlank(turkish)) turkish = translationService.translate(english, "en", "tr");
        } else {
            if (isBlank(estonian)) estonian = translationService.translate(turkish, "tr", "et");
            if (isBlank(english)) english = translationService.translate(turkish, "tr", "en");
        }

        Integer maxOrder = jdbc.queryForObject(
            "SELECT COALESCE(MAX(sort_order), -1) FROM candidate_sentences WHERE candidate_id = ?", Integer.class, candidateId);
        int sortOrder = (maxOrder != null ? maxOrder : -1) + 1;

        jdbc.update("INSERT INTO candidate_sentences (candidate_id, estonian, english, turkish, sort_order) VALUES (?, ?, ?, ?, ?)",
            candidateId, estonian, english, turkish, sortOrder);

        var result = new LinkedHashMap<String, Object>();
        result.put("estonian", estonian);
        result.put("english", english);
        result.put("turkish", turkish);
        result.put("sortOrder", sortOrder);
        return result;
    }

    public Map<String, Object> updateSentence(int candidateId, int sentenceId, String estonianIn, String englishIn, String turkishIn) {
        var rows = jdbc.queryForList("SELECT * FROM candidate_sentences WHERE id = ? AND candidate_id = ?", sentenceId, candidateId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Sentence not found");

        var existing = rows.get(0);
        String estonian = estonianIn != null ? estonianIn : (String) existing.get("estonian");
        String english = englishIn != null ? englishIn : (String) existing.get("english");
        String turkish = turkishIn != null ? turkishIn : (String) existing.get("turkish");

        // Auto-translate missing
        if (isBlank(estonian) && !isBlank(english)) estonian = translationService.translate(english, "en", "et");
        if (isBlank(english) && !isBlank(estonian)) english = translationService.translate(estonian, "et", "en");
        if (isBlank(turkish)) {
            if (!isBlank(estonian)) turkish = translationService.translate(estonian, "et", "tr");
            else if (!isBlank(english)) turkish = translationService.translate(english, "en", "tr");
        }

        jdbc.update("UPDATE candidate_sentences SET estonian = ?, english = ?, turkish = ? WHERE id = ?",
            estonian, english, turkish, sentenceId);

        return Map.of("id", sentenceId, "estonian", str(estonian), "english", str(english), "turkish", str(turkish));
    }

    public boolean deleteSentence(int candidateId, int sentenceId) {
        return jdbc.update("DELETE FROM candidate_sentences WHERE id = ? AND candidate_id = ?", sentenceId, candidateId) > 0;
    }

    // ─── Approve operations ──────────────────────────────

    public String approve(int id) {
        String wordId = candidateRepo.approveCandidate(id);
        if (wordId != null) wordBankService.reload();
        return wordId;
    }

    public Map<String, Object> approveAll(String level) {
        Map<String, Integer> result = candidateRepo.approveAll(level);
        if (result.get("approved") > 0) wordBankService.reload();
        return Map.of("approved", result.get("approved"), "skipped", result.get("skipped"), "total", result.get("total"));
    }

    public Map<String, Object> approveBatch(List<Integer> ids) {
        int approved = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (int id : ids) {
            try {
                String wordId = candidateRepo.approveCandidate(id);
                if (wordId != null) approved++;
                else failed++;
            } catch (Exception e) {
                failed++;
                errors.add(id + ": " + e.getMessage());
            }
        }
        if (approved > 0) wordBankService.reload();
        var result = new LinkedHashMap<String, Object>();
        result.put("approved", approved);
        result.put("failed", failed);
        if (!errors.isEmpty()) result.put("errors", errors);
        return result;
    }

    // ─── Bulk translate (background) ─────────────────────

    public void translateAllAsync() {
        Thread.startVirtualThread(() -> {
            long start = System.currentTimeMillis();
            int wordsDone = 0, sentencesDone = 0, wordsFailed = 0;

            // 1. Translate word-level Turkish for candidates missing it
            var wordsNeedingTurkish = jdbc.queryForList(
                "SELECT id, english FROM candidate_words WHERE (turkish IS NULL OR turkish = '') AND english IS NOT NULL AND english != '' AND status IN ('pending', 'translated')");

            for (var w : wordsNeedingTurkish) {
                try {
                    int wid = ((Number) w.get("id")).intValue();
                    String turkish = translationService.translate((String) w.get("english"), "en", "tr");
                    if (turkish != null) {
                        jdbc.update("UPDATE candidate_words SET turkish = ? WHERE id = ?", turkish, wid);
                        wordsDone++;
                    } else {
                        wordsFailed++;
                    }
                } catch (Exception e) {
                    wordsFailed++;
                    log.warn("[candidate-translate] Error translating word {}: {}", w.get("id"), e.getMessage());
                }
            }

            // 2. Translate ALL sentences missing English or Turkish
            var incompleteSentences = jdbc.queryForList("""
                SELECT cs.id, cs.candidate_id, cs.estonian, cs.english, cs.turkish
                FROM candidate_sentences cs
                JOIN candidate_words cw ON cw.id = cs.candidate_id
                WHERE cw.status IN ('pending', 'translated')
                  AND (cs.english IS NULL OR cs.english = '' OR cs.turkish IS NULL OR cs.turkish = '')
                  AND (cs.estonian IS NOT NULL AND cs.estonian != '')
                """);

            for (var s : incompleteSentences) {
                try {
                    String est = (String) s.get("estonian");
                    String en = (String) s.get("english");
                    String tr = (String) s.get("turkish");
                    boolean updated = false;

                    if (isBlank(en)) {
                        en = translationService.translate(est, "et", "en");
                        updated = true;
                    }
                    if (isBlank(tr)) {
                        tr = !isBlank(en) ? translationService.translate(en, "en", "tr")
                                          : translationService.translate(est, "et", "tr");
                        updated = true;
                    }
                    if (updated) {
                        jdbc.update("UPDATE candidate_sentences SET english = COALESCE(?, english), turkish = COALESCE(?, turkish) WHERE id = ?",
                            en, tr, s.get("id"));
                        sentencesDone++;
                    }
                } catch (Exception e) {
                    log.warn("[candidate-translate] Error translating sentence {}: {}", s.get("id"), e.getMessage());
                }
            }

            // 3. Mark status: only set 'translated' if word AND all its sentences have Turkish
            jdbc.update("""
                UPDATE candidate_words SET status = 'translated', translated_at = NOW()
                WHERE status = 'pending'
                  AND turkish IS NOT NULL AND turkish != ''
                  AND NOT EXISTS (
                    SELECT 1 FROM candidate_sentences cs
                    WHERE cs.candidate_id = candidate_words.id
                      AND (cs.turkish IS NULL OR cs.turkish = '')
                  )
                """);

            // Keep as 'pending' if sentences still missing Turkish
            jdbc.update("""
                UPDATE candidate_words SET status = 'pending'
                WHERE status = 'translated'
                  AND EXISTS (
                    SELECT 1 FROM candidate_sentences cs
                    WHERE cs.candidate_id = candidate_words.id
                      AND (cs.turkish IS NULL OR cs.turkish = '')
                  )
                """);

            long duration = (System.currentTimeMillis() - start) / 1000;
            notificationService.success("candidates", "Bulk Translation Complete",
                wordsDone + " words + " + sentencesDone + " sentences translated, " + wordsFailed + " failed (" + duration + "s)");
        });
    }

    // ─── Helpers ─────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(String s) {
        return s != null ? s : "";
    }
}
