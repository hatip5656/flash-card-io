package io.flashcard.controller;

import io.flashcard.config.AppProperties;
import io.flashcard.repository.WordDbRepository;
import io.flashcard.service.EkilexService;
import io.flashcard.service.WordBankService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/words")
public class AdminWordController {

    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");

    private final WordDbRepository wordDbRepo;
    private final WordBankService wordBankService;
    private final EkilexService ekilexService;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbc;

    public AdminWordController(WordDbRepository wordDbRepo, WordBankService wordBankService,
                               EkilexService ekilexService, AppProperties appProperties, JdbcTemplate jdbc) {
        this.wordDbRepo = wordDbRepo;
        this.wordBankService = wordBankService;
        this.ekilexService = ekilexService;
        this.appProperties = appProperties;
        this.jdbc = jdbc;
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

    @PostMapping("/from-ekilex")
    public ResponseEntity<?> addFromEkilex(@RequestBody Map<String, Object> body) {
        String apiKey = appProperties.getEkilexApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(500).body(Map.of("error", "EKILEX_API_KEY not configured"));
        }

        String search = (String) body.get("search");
        String level = (String) body.get("level");
        Integer count = body.get("count") != null ? ((Number) body.get("count")).intValue() : 10;

        List<Map<String, Object>> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        if (search != null) {
            var results = ekilexService.searchWord(search, apiKey);
            for (var w : results) {
                if (w.cefrLevel() == null || w.english() == null) continue;
                String id = w.cefrLevel().toLowerCase() + "-" + w.wordValue().toLowerCase().replaceAll("\\s+", "-");
                if (wordDbRepo.wordExists(w.wordValue().toLowerCase())) {
                    skipped.add(w.wordValue());
                    continue;
                }
                List<Map<String, String>> sentences = w.usages().stream()
                    .filter(u -> u.estonian() != null && !u.estonian().isBlank())
                    .map(u -> Map.of("estonian", u.estonian(), "english", u.english()))
                    .toList();
                String wordId = wordDbRepo.addWord(w.wordValue().toLowerCase(), w.english(), null, w.cefrLevel(), new ArrayList<>(sentences));
                if (wordId != null) {
                    added.add(Map.of("id", wordId, "estonian", w.wordValue(), "english", w.english(), "cefrLevel", w.cefrLevel()));
                }
            }
        } else if (level != null && VALID_LEVELS.contains(level)) {
            int target = Math.min(count, 50);
            Set<String> existingSet = new HashSet<>(jdbc.queryForList("SELECT estonian FROM words", String.class));
            for (int attempt = 0; attempt < target * 3 && added.size() < target; attempt++) {
                var w = ekilexService.getRandomWordForLevel(level, existingSet, apiKey);
                if (w == null || w.english() == null || existingSet.contains(w.wordValue().toLowerCase())) continue;
                existingSet.add(w.wordValue().toLowerCase());
                List<Map<String, String>> sentences = w.usages().stream()
                    .filter(u -> u.estonian() != null && !u.estonian().isBlank())
                    .map(u -> Map.of("estonian", u.estonian(), "english", u.english()))
                    .toList();
                String wordId = wordDbRepo.addWord(w.wordValue().toLowerCase(), w.english(), null, level, new ArrayList<>(sentences));
                if (wordId != null) {
                    added.add(Map.of("id", wordId, "estonian", w.wordValue(), "english", w.english(), "cefrLevel", level));
                } else {
                    skipped.add(w.wordValue());
                }
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide {search} or {level, count}"));
        }

        if (!added.isEmpty()) wordBankService.reload();
        return ResponseEntity.ok(Map.of("added", added.size(), "skipped", skipped.size(), "words", added));
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

    @GetMapping("/sentences/missing")
    public Map<String, Object> getMissingSentenceTranslations(
            @RequestParam(defaultValue = "turkish") String lang,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int total = wordDbRepo.countMissingTranslations(lang);
        List<Map<String, Object>> sentences = wordDbRepo.getSentencesWithMissingTranslations(lang, Math.min(limit, 500), offset);
        return Map.of("sentences", sentences, "count", sentences.size(), "total", total, "lang", lang, "offset", offset);
    }

    @GetMapping("/without-sentences")
    public Map<String, Object> getWordsWithoutSentences(
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int total = wordDbRepo.countWordsWithoutSentences();
        List<Map<String, Object>> words = wordDbRepo.getWordsWithoutSentences(Math.min(limit, 500), offset);
        return Map.of("words", words, "count", words.size(), "total", total, "offset", offset);
    }

    @GetMapping("/sentences/{wordId}")
    public Map<String, Object> getWordSentences(@PathVariable String wordId) {
        List<Map<String, Object>> sentences = wordDbRepo.getSentencesByWord(wordId);
        return Map.of("wordId", wordId, "sentences", sentences);
    }

    @PatchMapping("/sentences/{wordId}")
    public Map<String, Object> updateSentenceTranslation(
            @PathVariable String wordId,
            @RequestBody Map<String, String> body) {
        String sentenceEe = body.get("estonian");
        String english = body.get("english");
        String turkish = body.get("turkish");
        if (sentenceEe == null) return Map.of("error", "estonian sentence required");
        int updated = wordDbRepo.updateSentenceTranslation(wordId, sentenceEe, english, turkish);
        return Map.of("ok", true, "updated", updated);
    }

    @PostMapping("/sentences-batch")
    public Map<String, Object> batchUpdateSentenceTranslations(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        int updated = 0;
        int failed = 0;
        for (var item : items) {
            String wordId = item.get("wordId");
            String estonian = item.get("estonian");
            String english = item.get("english");
            String turkish = item.get("turkish");
            if (wordId == null || estonian == null) { failed++; continue; }
            try {
                updated += wordDbRepo.updateSentenceTranslation(wordId, estonian, english, turkish);
            } catch (Exception e) { failed++; }
        }
        return Map.of("ok", true, "updated", updated, "failed", failed, "total", items.size());
    }

    @PostMapping("/sentences/add-batch")
    public Map<String, Object> batchAddSentences(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        int inserted = 0;
        int failed = 0;
        for (var item : items) {
            String wordId = item.get("wordId");
            String estonian = item.get("estonian");
            String english = item.get("english");
            String turkish = item.get("turkish");
            String sortStr = item.get("sortOrder");
            int sortOrder = sortStr != null ? Integer.parseInt(sortStr) : 0;
            if (wordId == null || estonian == null) { failed++; continue; }
            try {
                wordDbRepo.insertSentence(wordId, estonian, english, turkish, sortOrder);
                inserted++;
            } catch (Exception e) { failed++; }
        }
        if (inserted > 0) wordBankService.reload();
        return Map.of("ok", true, "inserted", inserted, "failed", failed, "total", items.size());
    }

}
