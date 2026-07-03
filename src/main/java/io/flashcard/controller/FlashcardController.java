package io.flashcard.controller;

import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.GrammarRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.GrammarBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api")
public class FlashcardController {

    private final SubscriberRepository subscriberRepo;
    private final SentWordRepository sentWordRepo;
    private final GrammarRepository grammarRepo;
    private final GrammarBankService grammarBankService;

    public FlashcardController(SubscriberRepository subscriberRepo, SentWordRepository sentWordRepo,
                               GrammarRepository grammarRepo, GrammarBankService grammarBankService) {
        this.subscriberRepo = subscriberRepo;
        this.sentWordRepo = sentWordRepo;
        this.grammarRepo = grammarRepo;
        this.grammarBankService = grammarBankService;
    }

    @GetMapping("/flashcards/next")
    public ResponseEntity<Map<String, Object>> getNextFlashcard(HttpServletRequest request) {
        // Pre-built queue and live build logic are bot-specific (Telegram).
        // The Java API version returns 503 — flashcard building requires external TTS/image services
        // that are wired via the bot main loop in the Node.js version.
        return ResponseEntity.status(503).body(Map.of("error", "Flashcard building not available via API-only mode. Use the feed endpoint instead."));
    }

    @GetMapping("/flashcards/audio/latest")
    public ResponseEntity<Map<String, Object>> getAudio(HttpServletRequest request) {
        return ResponseEntity.status(404).body(Map.of("error", "No audio available"));
    }

    @GetMapping("/flashcards/grammar")
    public ResponseEntity<?> getGrammarCard(HttpServletRequest request) {
        long chatId = getUserId(request);
        String level = subscriberRepo.getSubscriberLevel(chatId);
        Set<String> sentIds = grammarRepo.getSentGrammarIds(chatId);
        GrammarLesson lesson = grammarBankService.getRandomLesson(level, sentIds);

        if (lesson == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No grammar lessons available for your level"));
        }

        grammarRepo.markGrammarSent(chatId, lesson.id());
        return ResponseEntity.ok(Map.of(
            "id", lesson.id(),
            "topic", lesson.topic(),
            "cefrLevel", lesson.cefrLevel(),
            "content", lesson.content()));
    }

    @GetMapping("/review/due")
    public List<Map<String, Object>> getDueWords(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "10") int limit) {
        long chatId = getUserId(request);
        return sentWordRepo.getWordsDueForReview(chatId, Math.min(limit, 50));
    }

    @PostMapping("/review/recall")
    public ResponseEntity<?> submitRecall(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String wordValue = (String) body.get("wordValue");
        Object qualityObj = body.get("quality");

        if (wordValue == null || qualityObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordValue and quality (0-5) are required"));
        }

        int quality;
        try {
            quality = ((Number) qualityObj).intValue();
        } catch (ClassCastException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordValue and quality (0-5) are required"));
        }

        if (quality < 0 || quality > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "wordValue and quality (0-5) are required"));
        }

        sentWordRepo.updateSm2(chatId, wordValue, quality);
        return ResponseEntity.ok(Map.of("wordValue", wordValue, "quality", quality, "updated", true));
    }
}
