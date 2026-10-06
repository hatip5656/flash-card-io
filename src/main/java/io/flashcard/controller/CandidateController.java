package io.flashcard.controller;

import io.flashcard.repository.CandidateRepository;
import io.flashcard.service.TranslationService;
import io.flashcard.service.WordBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/candidates")
public class CandidateController {

    private final CandidateRepository candidateRepo;
    private final WordBankService wordBankService;
    private final TranslationService translationService;
    private final JdbcTemplate jdbc;

    public CandidateController(CandidateRepository candidateRepo, WordBankService wordBankService,
                                TranslationService translationService, JdbcTemplate jdbc) {
        this.candidateRepo = candidateRepo;
        this.wordBankService = wordBankService;
        this.translationService = translationService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> listCandidates(@RequestParam(defaultValue = "pending") String status,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(defaultValue = "20") int limit) {
        return candidateRepo.listCandidates(status, level, Math.min(limit, 100));
    }

    @GetMapping("/stats")
    public Map<String, Object> candidateStats() {
        return candidateRepo.candidateStats();
    }

    @PatchMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> translateCandidate(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String turkish = (String) body.get("turkish");
        if (turkish == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "turkish translation required"));
        }
        List<Map<String, String>> sentences = (List<Map<String, String>>) body.get("sentences");
        Map<String, Object> result = candidateRepo.translateCandidate(id, turkish, sentences);
        if (result == null) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        return ResponseEntity.ok(result);
    }

    /**
     * Auto-translate a candidate's Turkish field using the translation service.
     * Translates english → turkish.
     */
    @PostMapping("/{id}/auto-translate")
    public ResponseEntity<?> autoTranslate(@PathVariable int id) {
        var rows = jdbc.queryForList("SELECT id, estonian, english, turkish FROM candidate_words WHERE id = ?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));

        var candidate = rows.get(0);
        String english = (String) candidate.get("english");
        if (english == null || english.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No English translation to translate from"));
        }

        String turkish = translationService.translate(english, "en", "tr");
        if (turkish == null) {
            return ResponseEntity.status(502).body(Map.of("error", "Translation service failed"));
        }

        // Also translate sentences
        var sentenceRows = jdbc.queryForList(
            "SELECT id, estonian, english, turkish FROM candidate_sentences WHERE candidate_id = ? ORDER BY sort_order", id);
        int sentencesUpdated = 0;
        for (var s : sentenceRows) {
            if (s.get("turkish") != null && !((String) s.get("turkish")).isBlank()) continue;
            String sentenceEn = (String) s.get("english");
            if (sentenceEn == null || sentenceEn.isBlank()) continue;
            String sentenceTr = translationService.translate(sentenceEn, "en", "tr");
            if (sentenceTr != null) {
                jdbc.update("UPDATE candidate_sentences SET turkish = ? WHERE id = ?", sentenceTr, s.get("id"));
                sentencesUpdated++;
            }
        }

        // Update candidate
        jdbc.update("UPDATE candidate_words SET turkish = ?, status = 'translated', translated_at = NOW() WHERE id = ?", turkish, id);

        return ResponseEntity.ok(Map.of("turkish", turkish, "sentencesTranslated", sentencesUpdated));
    }

    /**
     * Add or generate a sentence for a candidate.
     * Provide text in any language (estonian, english, or turkish) and the others will be auto-translated.
     */
    @PostMapping("/{id}/sentences")
    public ResponseEntity<?> addSentence(@PathVariable int id, @RequestBody Map<String, String> body) {
        var rows = jdbc.queryForList("SELECT id FROM candidate_words WHERE id = ?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));

        String estonian = body.get("estonian");
        String english = body.get("english");
        String turkish = body.get("turkish");

        // Need at least one language provided
        if ((estonian == null || estonian.isBlank()) && (english == null || english.isBlank()) && (turkish == null || turkish.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide at least one sentence in any language"));
        }

        // Auto-translate missing languages
        if (estonian != null && !estonian.isBlank()) {
            if (english == null || english.isBlank()) {
                english = translationService.translate(estonian, "et", "en");
            }
            if (turkish == null || turkish.isBlank()) {
                turkish = translationService.translate(estonian, "et", "tr");
            }
        } else if (english != null && !english.isBlank()) {
            if (estonian == null || estonian.isBlank()) {
                estonian = translationService.translate(english, "en", "et");
            }
            if (turkish == null || turkish.isBlank()) {
                turkish = translationService.translate(english, "en", "tr");
            }
        } else if (turkish != null && !turkish.isBlank()) {
            if (estonian == null || estonian.isBlank()) {
                estonian = translationService.translate(turkish, "tr", "et");
            }
            if (english == null || english.isBlank()) {
                english = translationService.translate(turkish, "tr", "en");
            }
        }

        // Get next sort order
        Integer maxOrder = jdbc.queryForObject(
            "SELECT COALESCE(MAX(sort_order), -1) FROM candidate_sentences WHERE candidate_id = ?", Integer.class, id);
        int sortOrder = (maxOrder != null ? maxOrder : -1) + 1;

        jdbc.update(
            "INSERT INTO candidate_sentences (candidate_id, estonian, english, turkish, sort_order) VALUES (?, ?, ?, ?, ?)",
            id, estonian, english, turkish, sortOrder);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("estonian", estonian);
        result.put("english", english);
        result.put("turkish", turkish);
        result.put("sortOrder", sortOrder);
        return ResponseEntity.ok(result);
    }

    /**
     * Update an existing sentence — fill in missing translations or correct text.
     */
    @PatchMapping("/{id}/sentences/{sentenceId}")
    public ResponseEntity<?> updateSentence(@PathVariable int id, @PathVariable int sentenceId,
                                             @RequestBody Map<String, String> body) {
        var rows = jdbc.queryForList("SELECT * FROM candidate_sentences WHERE id = ? AND candidate_id = ?", sentenceId, id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Sentence not found"));

        var existing = rows.get(0);
        String estonian = body.containsKey("estonian") ? body.get("estonian") : (String) existing.get("estonian");
        String english = body.containsKey("english") ? body.get("english") : (String) existing.get("english");
        String turkish = body.containsKey("turkish") ? body.get("turkish") : (String) existing.get("turkish");

        // Auto-translate any still-missing fields based on what we have
        if ((estonian == null || estonian.isBlank()) && english != null && !english.isBlank()) {
            estonian = translationService.translate(english, "en", "et");
        }
        if ((english == null || english.isBlank()) && estonian != null && !estonian.isBlank()) {
            english = translationService.translate(estonian, "et", "en");
        }
        if ((turkish == null || turkish.isBlank()) && estonian != null && !estonian.isBlank()) {
            turkish = translationService.translate(estonian, "et", "tr");
        } else if ((turkish == null || turkish.isBlank()) && english != null && !english.isBlank()) {
            turkish = translationService.translate(english, "en", "tr");
        }

        jdbc.update("UPDATE candidate_sentences SET estonian = ?, english = ?, turkish = ? WHERE id = ?",
            estonian, english, turkish, sentenceId);

        return ResponseEntity.ok(Map.of("id", sentenceId, "estonian", estonian, "english", english, "turkish", turkish));
    }

    /**
     * Delete a specific sentence from a candidate.
     */
    @DeleteMapping("/{id}/sentences/{sentenceId}")
    public ResponseEntity<?> deleteSentence(@PathVariable int id, @PathVariable int sentenceId) {
        int deleted = jdbc.update("DELETE FROM candidate_sentences WHERE id = ? AND candidate_id = ?", sentenceId, id);
        return ResponseEntity.ok(Map.of("deleted", deleted > 0));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCandidate(@PathVariable int id) {
        try {
            String wordId = candidateRepo.approveCandidate(id);
            if (wordId == null) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
            wordBankService.reload();
            return ResponseEntity.ok(Map.of("wordId", wordId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/approve-all")
    public Map<String, Object> approveAll(@RequestParam(required = false) String level) {
        Map<String, Integer> result = candidateRepo.approveAll(level);
        if (result.get("approved") > 0) wordBankService.reload();
        return Map.of("approved", result.get("approved"), "skipped", result.get("skipped"), "total", result.get("total"));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> rejectCandidate(@PathVariable int id) {
        candidateRepo.rejectCandidate(id);
        return Map.of("ok", true);
    }
}
