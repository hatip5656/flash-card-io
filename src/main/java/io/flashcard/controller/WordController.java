package io.flashcard.controller;

import io.flashcard.model.Word;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.repository.WordDbRepository;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/words")
public class WordController {

    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");

    private final WordBankService wordBankService;
    private final WordDbRepository wordDbRepo;
    private final SubscriberRepository subscriberRepo;

    public WordController(WordBankService wordBankService, WordDbRepository wordDbRepo,
                          SubscriberRepository subscriberRepo) {
        this.wordBankService = wordBankService;
        this.wordDbRepo = wordDbRepo;
        this.subscriberRepo = subscriberRepo;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q,
                                       @RequestParam(required = false) String level,
                                       @RequestParam(defaultValue = "20") int limit) {
        if (q == null || q.isBlank()) {
            return Map.of("results", List.of(), "total", 0);
        }

        String query = q.trim().toLowerCase();
        limit = Math.min(limit, 50);

        var stream = wordBankService.getAllWords().stream()
            .filter(w -> w.getEstonian().toLowerCase().contains(query)
                || w.getEnglish().toLowerCase().contains(query)
                || (w.getTurkish() != null && w.getTurkish().toLowerCase().contains(query)));

        if (level != null && VALID_LEVELS.contains(level)) {
            stream = stream.filter(w -> w.getCefrLevel().equals(level));
        }

        List<Map<String, Object>> results = stream
            .limit(limit)
            .map(this::wordToMap)
            .toList();

        return Map.of("results", results, "total", results.size(), "query", q.trim());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getWord(@PathVariable String id) {
        Word word = wordBankService.getWordById(id);
        if (word == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Word not found"));
        }
        return ResponseEntity.ok(wordToMap(word));
    }

    @PostMapping
    public ResponseEntity<?> addWord(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);

        String estonian = (String) body.get("estonian");
        String english = (String) body.get("english");
        String turkish = (String) body.get("turkish");
        String cefrLevel = (String) body.get("cefrLevel");

        if (estonian == null || estonian.isBlank() || english == null || english.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "estonian and english are required"));
        }

        if (cefrLevel == null || !VALID_LEVELS.contains(cefrLevel)) {
            cefrLevel = subscriberRepo.getSubscriberLevel(chatId);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> sentences = (List<Map<String, String>>) body.get("sentences");

        String id = wordDbRepo.addWord(estonian, english, turkish, cefrLevel, sentences);
        if (id == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Word already exists"));
        }

        wordBankService.reload();

        Word added = wordBankService.getWordById(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(added != null ? wordToMap(added) : Map.of("id", id));
    }

    @GetMapping("/exists")
    public Map<String, Object> exists(@RequestParam String estonian) {
        boolean found = wordDbRepo.wordExists(estonian.trim().toLowerCase());
        return Map.of("exists", found, "estonian", estonian.trim());
    }

    private Map<String, Object> wordToMap(Word w) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", w.getId());
        map.put("estonian", w.getEstonian());
        map.put("english", w.getEnglish());
        map.put("turkish", w.getTurkish());
        map.put("cefrLevel", w.getCefrLevel());
        map.put("sentences", w.getSentences() != null
            ? w.getSentences().stream().map(s -> Map.of(
                "estonian", s.estonian() != null ? s.estonian() : "",
                "english", s.english() != null ? s.english() : "",
                "turkish", s.turkish() != null ? s.turkish() : ""))
              .toList()
            : List.of());
        return map;
    }
}
