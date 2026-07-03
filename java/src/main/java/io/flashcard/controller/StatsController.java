package io.flashcard.controller;

import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.SavedWordRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.QuizRepository;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final ActivityRepository activityRepo;
    private final QuizRepository quizRepo;
    private final SentWordRepository sentWordRepo;
    private final SavedWordRepository savedWordRepo;
    private final WordBankService wordBankService;

    public StatsController(ActivityRepository activityRepo, QuizRepository quizRepo,
                           SentWordRepository sentWordRepo, SavedWordRepository savedWordRepo,
                           WordBankService wordBankService) {
        this.activityRepo = activityRepo;
        this.quizRepo = quizRepo;
        this.sentWordRepo = sentWordRepo;
        this.savedWordRepo = savedWordRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping("/users/me/stats")
    public Map<String, Object> getUserStats(HttpServletRequest request) {
        long chatId = getUserId(request);
        Map<String, Object> stats = activityRepo.getStats(chatId);
        Map<String, Object> quizStats = quizRepo.getQuizStats(chatId);
        int streak = activityRepo.getStreak(chatId);
        Map<String, Object> today = activityRepo.getTodayActivity(chatId);
        Map<String, Object> wordCounts = sentWordRepo.getWordCounts(chatId);
        int savedCount = savedWordRepo.countSavedWords(chatId);

        String level = (String) stats.get("level");
        int sent = (int) stats.get("sent");
        int totalForLevel = wordBankService.getWordsForLevel(level).size();
        int totalWords = wordBankService.getAllWords().size();
        int progress = totalForLevel > 0 ? Math.round((float) sent / totalForLevel * 100) : 0;

        int totalQuizzes = ((Number) quizStats.get("total")).intValue();
        double avgPct = ((Number) quizStats.get("avg_pct")).doubleValue();
        Number recentAvg = (Number) quizStats.get("recent_avg");
        Number olderAvg = (Number) quizStats.get("older_avg");
        Integer recentTrend = null;
        if (totalQuizzes >= 4 && olderAvg != null) {
            recentTrend = (int) Math.round(recentAvg.doubleValue() - olderAvg.doubleValue());
        }

        String streakEmoji = io.flashcard.service.TextUtils.streakEmoji(streak);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("schedule", stats.get("schedule"));
        result.put("wordsLearned", sent);
        result.put("totalWordsForLevel", totalForLevel);
        result.put("totalWordsInCatalog", totalWords);
        result.put("progressPercent", progress);
        result.put("streak", streak);
        result.put("streakEmoji", streakEmoji);
        result.put("today", today);
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
        record QuizStats(int totalQuizzes, int avgPercentage, Integer recentTrend) {}
        result.put("quiz", new QuizStats(totalQuizzes, (int) Math.round(avgPct), recentTrend));
        result.put("words", Map.of(
            "seen", ((Number) wordCounts.get("seen")).intValue(),
            "mastered", ((Number) wordCounts.get("mastered")).intValue(),
            "quizzed", ((Number) wordCounts.get("quizzed")).intValue(),
            "saved", savedCount,
            "totalQuizAnswers", ((Number) wordCounts.get("total_quiz_answers")).intValue(),
            "totalFeedViews", ((Number) wordCounts.get("total_feed_views")).intValue(),
            "totalCrushFinds", ((Number) wordCounts.get("total_crush_finds")).intValue()));
        return result;
    }
}
