package io.flashcard.controller;

import io.flashcard.model.QuizAnswer;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.service.DailyGoalService;
import io.flashcard.service.QuizBankService;
import io.flashcard.service.QuizGenerator;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/mobile/quiz")
public class MobileQuizController {

    private final QuizRepository quizRepo;
    private final SentWordRepository sentWordRepo;
    private final SubscriberRepository subscriberRepo;
    private final ActivityRepository activityRepo;
    private final WordBankService wordBankService;

    private final DailyGoalService dailyGoals;
    private final QuizBankService quizBank;

    public MobileQuizController(QuizRepository quizRepo, SentWordRepository sentWordRepo,
                                SubscriberRepository subscriberRepo, ActivityRepository activityRepo,
                                WordBankService wordBankService, DailyGoalService dailyGoals,
                                QuizBankService quizBank) {
        this.quizBank = quizBank;
        this.dailyGoals = dailyGoals;
        this.quizRepo = quizRepo;
        this.sentWordRepo = sentWordRepo;
        this.subscriberRepo = subscriberRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping("/generate")
    public ResponseEntity<?> generateQuiz(HttpServletRequest request,
                                           @RequestParam(defaultValue = "10") int count,
                                           // false for the feed cards and the widget, which can't take typed answers
                                           @RequestParam(defaultValue = "true") boolean typed) {
        long chatId = getUserId(request);
        count = Math.min(Math.max(count, 5), 50);

        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        boolean useTurkish = "turkish".equals(prefs.getNativeLanguage());

        List<Map<String, Object>> allWords = sentWordRepo.getLearnedWordsForQuiz(chatId);
        if (allWords.size() < 4) {
            String msg = useTurkish
                ? "Quiz ba\u015flatmak i\u00e7in en az 4 kelime \u00f6\u011frenmelisin."
                : "Need at least 4 learned words to start a quiz.";
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }

        List<Map<String, Object>> missed = quizRepo.getMostMissedWords(chatId, 10);
        Set<String> missedSet = missed.stream().map(m -> (String) m.get("estonian")).collect(Collectors.toSet());

        int maxMissed = (int) Math.ceil(count * 0.3);
        List<Map<String, Object>> missedWords = new ArrayList<>(allWords.stream()
            .filter(w -> missedSet.contains(w.get("word_value"))).toList());
        Collections.shuffle(missedWords);
        missedWords = missedWords.subList(0, Math.min(maxMissed, missedWords.size()));

        Set<String> missedIds = missedWords.stream().map(w -> (String) w.get("word_value")).collect(Collectors.toSet());
        List<Map<String, Object>> otherWords = new ArrayList<>(allWords.stream()
            .filter(w -> !missedIds.contains(w.get("word_value"))).toList());
        // Keep DB order: viewed-but-never-quizzed first, then by ease_factor (struggling)

        // Missed words first, then the rest in the database's order; words that can't make a fair
        // question are skipped and the next one takes their place
        List<String> candidates = new ArrayList<>(missedWords.stream().map(w -> (String) w.get("word_value")).toList());
        otherWords.forEach(w -> candidates.add((String) w.get("word_value")));
        List<String> learned = allWords.stream().map(w -> (String) w.get("word_value")).toList();

        Random rnd = new Random();
        List<Map<String, Object>> questions = new QuizGenerator(quizBank, wordBankService.getAllWords())
            .questions(candidates, learned, count, useTurkish, typed, subscriberRepo.getSubscriberLevel(chatId), rnd);
        Collections.shuffle(questions, rnd);
        for (int i = 0; i < questions.size(); i++) questions.get(i).put("index", i);

        return ResponseEntity.ok(Map.of("totalQuestions", questions.size(), "questions", questions));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitQuiz(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answersRaw = (List<Map<String, Object>>) body.get("answers");

        if (answersRaw == null || answersRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "answers array required"));
        }

        List<QuizAnswer> answers = answersRaw.stream()
            .map(a -> new QuizAnswer(
                (String) a.get("estonian"),
                (String) a.get("correctAnswer"),
                (String) a.get("userAnswer"),
                (Boolean) a.get("isCorrect")))
            .toList();

        int score = (int) answers.stream().filter(QuizAnswer::isCorrect).count();
        int total = answers.size();

        // Before the reviews below move the words' due dates: the goal counts answers to words that were due
        dailyGoals.recordQuizAnswers(chatId, answers.stream().map(QuizAnswer::estonian).filter(java.util.Objects::nonNull).toList());
        quizRepo.saveQuizResult(chatId, score, total, answers);
        activityRepo.logQuizActivity(chatId);
        sentWordRepo.incrementQuizCount(chatId, answers.stream().map(QuizAnswer::estonian).toList());

        for (QuizAnswer answer : answers) {
            int quality = answer.isCorrect() ? 4 : 1;
            sentWordRepo.updateSm2(chatId, answer.estonian(), quality);
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        // Words this quiz brought to their checkpoint, asked about on the results screen
        response.put("masteryPrompts", answers.stream().filter(QuizAnswer::isCorrect).map(QuizAnswer::estonian)
            .filter(java.util.Objects::nonNull).distinct()
            .flatMap(w -> sentWordRepo.masteryPromptFor(chatId, w).stream()).toList());
        response.put("score", score);
        response.put("total", total);
        response.put("percentage", Math.round((float) score / total * 100));

        String level = subscriberRepo.getSubscriberLevel(chatId);
        if (!"B2".equals(level)) {
            int totalForLevel = wordBankService.getWordsForLevel(level).size();
            Map<String, Object> readiness = sentWordRepo.getLevelReadiness(chatId, level);
            int strongWords = ((Number) readiness.get("strong")).intValue();
            int requiredStrong = (int) Math.ceil(totalForLevel * 0.6);
            double avgEase = ((Number) readiness.get("avg_ease")).doubleValue();
            if (strongWords >= requiredStrong && avgEase >= 2.0) {
                String nextLevel = switch (level) {
                    case "A1" -> "A2";
                    case "A2" -> "B1";
                    case "B1" -> "B2";
                    default -> null;
                };
                if (nextLevel != null) {
                    response.put("suggestLevelUp", Map.of("nextLevel", nextLevel));
                }
            }
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/answer")
    public Map<String, Object> submitSingleAnswer(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String estonian = (String) body.get("estonian");
        String correctAnswer = (String) body.get("correctAnswer");
        String userAnswer = (String) body.get("userAnswer");
        boolean isCorrect = Boolean.TRUE.equals(body.get("isCorrect"));

        if (estonian == null) return Map.of("error", "estonian required");

        // Track the answer; the goal first, while the word's due date is unchanged
        dailyGoals.recordQuizAnswers(chatId, java.util.List.of(estonian));
        sentWordRepo.incrementQuizCount(chatId, java.util.List.of(estonian));
        int quality = isCorrect ? 4 : 1;
        sentWordRepo.updateSm2(chatId, estonian, quality);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("ok", true);
        result.put("isCorrect", isCorrect);
        // This answer may have reached the word's checkpoint: the app asks "did you master it?"
        if (isCorrect) sentWordRepo.masteryPromptFor(chatId, estonian).ifPresent(p -> result.put("askMastery", p));
        return result;
    }

}
