package io.flashcard.controller;

import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.GeminiService;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final GeminiService geminiService;
    private final SentWordRepository sentWordRepo;
    private final SubscriberRepository subscriberRepo;
    private final QuizRepository quizRepo;
    private final ActivityRepository activityRepo;
    private final WordBankService wordBankService;

    public ChatbotController(GeminiService geminiService, SentWordRepository sentWordRepo,
                             SubscriberRepository subscriberRepo, QuizRepository quizRepo,
                             ActivityRepository activityRepo, WordBankService wordBankService) {
        this.geminiService = geminiService;
        this.sentWordRepo = sentWordRepo;
        this.subscriberRepo = subscriberRepo;
        this.quizRepo = quizRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
    }

    @PostMapping("/message")
    public Map<String, Object> sendMessage(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        if (!geminiService.isAvailable()) {
            return Map.of("error", "Chatbot not configured");
        }

        long chatId = getUserId(request);
        String userMessage = (String) body.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());

        if (userMessage.isBlank()) {
            return Map.of("error", "Empty message");
        }

        String learnerContext = buildLearnerContext(chatId);
        String reply = geminiService.chat(userMessage, history, learnerContext);
        if (reply != null) {
            return Map.of("reply", reply);
        }
        return Map.of("error", "No response from AI");
    }

    private String buildLearnerContext(long chatId) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        var prefs = subscriberRepo.getPreferences(chatId);
        String nativeLang = prefs != null ? prefs.getNativeLanguage() : "turkish";

        int streak = activityRepo.getStreak(chatId);
        var wordCounts = sentWordRepo.getWordCounts(chatId);
        var quizStats = quizRepo.getQuizStats(chatId);

        int seen = ((Number) wordCounts.get("seen")).intValue();
        int mastered = ((Number) wordCounts.get("mastered")).intValue();
        int quizzed = ((Number) wordCounts.get("quizzed")).intValue();
        int totalQuizzes = ((Number) quizStats.get("total")).intValue();
        int avgPct = (int) Math.round(((Number) quizStats.get("avg_pct")).doubleValue());

        // Weak words (struggling)
        var weakWords = sentWordRepo.getWeakWords(chatId, 10);
        String weakList = weakWords.stream()
            .map(w -> "\"" + w.get("word_value") + "\" (" + w.get("english") + ", ease=" + String.format("%.1f", ((Number) w.get("ease_factor")).doubleValue()) + ")")
            .collect(Collectors.joining(", "));

        // Most missed in quizzes
        var missedWords = quizRepo.getMostMissedWords(chatId, 8);
        String missedList = missedWords.stream()
            .map(w -> "\"" + w.get("estonian") + "\" (missed " + w.get("mistakes") + "x)")
            .collect(Collectors.joining(", "));

        // Recently learned
        var vocab = sentWordRepo.getVocabularyCollection(chatId);
        String recentList = vocab.stream()
            .limit(10)
            .map(w -> "\"" + w.get("word_value") + "\" (" + (w.get("turkish") != null ? w.get("turkish") : w.get("english")) + ")")
            .collect(Collectors.joining(", "));

        // Level readiness
        var readiness = sentWordRepo.getLevelReadiness(chatId, level);
        int strongWords = ((Number) readiness.get("strong")).intValue();
        int totalForLevel = wordBankService.getWordsForLevel(level).size();

        StringBuilder ctx = new StringBuilder();
        ctx.append("CEFR Level: ").append(level).append("\n");
        ctx.append("Native language: ").append(nativeLang).append("\n");
        ctx.append("Streak: ").append(streak).append(" days\n");
        ctx.append("Vocabulary: ").append(seen).append(" seen, ").append(mastered).append(" mastered, ").append(quizzed).append(" quizzed\n");
        ctx.append("Level progress: ").append(strongWords).append("/").append(totalForLevel).append(" strong words\n");
        ctx.append("Quiz performance: ").append(totalQuizzes).append(" quizzes, avg ").append(avgPct).append("%\n");
        if (!weakList.isEmpty()) ctx.append("Weak words (low ease): ").append(weakList).append("\n");
        if (!missedList.isEmpty()) ctx.append("Most missed in quizzes: ").append(missedList).append("\n");
        if (!recentList.isEmpty()) ctx.append("Recently learned: ").append(recentList).append("\n");
        return ctx.toString();
    }
}
