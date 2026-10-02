package io.flashcard.controller;

import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.WordBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final SubscriberRepository subscriberRepo;
    private final SentWordRepository sentWordRepo;
    private final QuizRepository quizRepo;
    private final ActivityRepository activityRepo;
    private final WordBankService wordBankService;

    public AdminUserController(SubscriberRepository subscriberRepo, SentWordRepository sentWordRepo,
                               QuizRepository quizRepo, ActivityRepository activityRepo,
                               WordBankService wordBankService) {
        this.subscriberRepo = subscriberRepo;
        this.sentWordRepo = sentWordRepo;
        this.quizRepo = quizRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean active) {
        var users = subscriberRepo.getAllSubscribers(limit, offset, level, active);
        int total = subscriberRepo.countSubscribers(level, active);
        return ResponseEntity.ok(Map.of("items", users, "total", total));
    }

    @GetMapping("/{chatId}/stats")
    public ResponseEntity<?> getUserStats(@PathVariable long chatId) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        if (level == null) return ResponseEntity.notFound().build();

        var prefs = subscriberRepo.getPreferences(chatId);
        int streak = activityRepo.getStreak(chatId);
        var wordCounts = sentWordRepo.getWordCounts(chatId);
        var quizStats = quizRepo.getQuizStats(chatId);
        var readiness = sentWordRepo.getLevelReadiness(chatId, level);
        int totalForLevel = wordBankService.getWordsForLevel(level).size();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("chatId", chatId);
        stats.put("level", level);
        stats.put("preferences", prefs);
        stats.put("streak", streak);
        stats.put("words", wordCounts);
        stats.put("quiz", quizStats);
        stats.put("readiness", readiness);
        stats.put("totalWordsForLevel", totalForLevel);
        return ResponseEntity.ok(stats);
    }

    @PatchMapping("/{chatId}/level")
    public ResponseEntity<?> changeLevel(@PathVariable long chatId, @RequestBody Map<String, String> body) {
        String level = body.get("level");
        if (level == null) return ResponseEntity.badRequest().body(Map.of("error", "Level required"));
        subscriberRepo.setSubscriberLevel(chatId, level);
        return ResponseEntity.ok(Map.of("chatId", chatId, "level", level));
    }
}
