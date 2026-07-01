package io.flashcard.controller;

import io.flashcard.repository.WordDbRepository;
import io.flashcard.service.WordBankService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/words")
public class AdminWordController {

    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");

    private final WordDbRepository wordDbRepo;
    private final WordBankService wordBankService;

    public AdminWordController(WordDbRepository wordDbRepo, WordBankService wordBankService) {
        this.wordDbRepo = wordDbRepo;
        this.wordBankService = wordBankService;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> addWord(@RequestBody Map<String, Object> body) {
        String estonian = (String) body.get("estonian");
        String english = (String) body.get("english");
        String turkish = (String) body.get("turkish");
        String cefrLevel = (String) body.get("cefrLevel");

        if (estonian == null || english == null || cefrLevel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "estonian, english, cefrLevel required"));
        }
        if (!VALID_LEVELS.contains(cefrLevel)) {
            return ResponseEntity.badRequest().body(Map.of("error", "cefrLevel must be one of: " + String.join(", ", VALID_LEVELS)));
        }

        List<Map<String, String>> sentences = (List<Map<String, String>>) body.get("sentences");
        String id = wordDbRepo.addWord(estonian, english, turkish, cefrLevel, sentences);
        if (id == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Word already exists"));
        }

        wordBankService.reload();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id", id, "estonian", estonian, "english", english, "turkish", turkish, "cefrLevel", cefrLevel));
    }

    @GetMapping("/untranslated")
    public Map<String, Object> getUntranslated(@RequestParam(required = false) String level,
                                                @RequestParam(defaultValue = "50") int limit) {
        String lvl = level != null && VALID_LEVELS.contains(level) ? level : null;
        var words = wordDbRepo.getUntranslated(lvl, Math.min(limit, 200));
        return Map.of("count", words.size(), "words", words);
    }

    @GetMapping("/untranslated-full")
    public Map<String, Object> getUntranslatedFull(@RequestParam(required = false) String level,
                                                    @RequestParam(defaultValue = "10") int limit) {
        String lvl = level != null && VALID_LEVELS.contains(level) ? level : null;
        var words = wordDbRepo.getUntranslated(lvl, Math.min(limit, 50));
        // Attach sentences for each word
        List<Map<String, Object>> result = words.stream().map(w -> {
            Map<String, Object> detail = wordDbRepo.getWordDetail((String) w.get("id"));
            return detail != null ? detail : new HashMap<>(w);
        }).toList();
        return Map.of("count", result.size(), "words", result);
    }

    @GetMapping("/stats")
    public Map<String, Object> getWordStats() {
        var levels = wordDbRepo.getWordStats();
        int totalWords = levels.stream().mapToInt(r -> ((Number) r.get("total")).intValue()).sum();
        int totalSentences = wordDbRepo.countSentences();
        return Map.of("levels", levels, "totalWords", totalWords, "totalSentences", totalSentences);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getWordDetail(@PathVariable String id) {
        Map<String, Object> detail = wordDbRepo.getWordDetail(id);
        if (detail == null) return ResponseEntity.status(404).body(Map.of("error", "Word not found"));
        return ResponseEntity.ok(detail);
    }

    @PatchMapping("/{id}/translate")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> translateWord(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String turkish = (String) body.get("turkish");
        if (turkish == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "turkish translation required"));
        }
        int updated = wordDbRepo.translateWord(id, turkish);
        if (updated == 0) return ResponseEntity.status(404).body(Map.of("error", "Word not found"));

        int sentencesUpdated = 0;
        List<Map<String, String>> sentences = (List<Map<String, String>>) body.get("sentences");
        if (sentences != null) {
            for (Map<String, String> s : sentences) {
                if (s.get("estonian") != null && s.get("turkish") != null) {
                    sentencesUpdated += wordDbRepo.translateSentence(id, s.get("estonian"), s.get("turkish"));
                }
            }
        }

        Map<String, Object> detail = wordDbRepo.getWordDetail(id);
        if (detail != null) detail.put("sentencesUpdated", sentencesUpdated);
        return ResponseEntity.ok(detail);
    }

    @PostMapping("/bulk-translate")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> bulkTranslate(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> translations = (List<Map<String, Object>>) body.get("translations");
        if (translations == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "translations array required"));
        }

        int wordsUpdated = 0, sentencesUpdated = 0;
        for (Map<String, Object> t : translations) {
            String tId = (String) t.get("id");
            String turkish = (String) t.get("turkish");
            if (tId == null || turkish == null) continue;
            wordsUpdated += wordDbRepo.translateWord(tId, turkish);

            List<Map<String, String>> sentences = (List<Map<String, String>>) t.get("sentences");
            if (sentences != null) {
                for (Map<String, String> s : sentences) {
                    if (s.get("estonian") != null && s.get("turkish") != null) {
                        sentencesUpdated += wordDbRepo.translateSentence(tId, s.get("estonian"), s.get("turkish"));
                    }
                }
            }
        }

        if (wordsUpdated > 0) wordBankService.reload();
        return ResponseEntity.ok(Map.of("wordsUpdated", wordsUpdated, "sentencesUpdated", sentencesUpdated, "total", translations.size()));
    }
}
