package io.flashcard.controller;

import io.flashcard.model.QuizAnswer;
import io.flashcard.model.UserPreferences;
import io.flashcard.model.Word;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.repository.ActivityRepository;
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

    public MobileQuizController(QuizRepository quizRepo, SentWordRepository sentWordRepo,
                                SubscriberRepository subscriberRepo, ActivityRepository activityRepo,
                                WordBankService wordBankService) {
        this.quizRepo = quizRepo;
        this.sentWordRepo = sentWordRepo;
        this.subscriberRepo = subscriberRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping("/generate")
    public ResponseEntity<?> generateQuiz(HttpServletRequest request,
                                           @RequestParam(defaultValue = "10") int count) {
        long chatId = getUserId(request);
        count = Math.min(Math.max(count, 5), 50);

        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        boolean useTurkish = "turkish".equals(prefs.getNativeLanguage());
        Map<String, String> turkishLookup = useTurkish ? buildTurkishLookup() : Map.of();

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
        Collections.shuffle(otherWords);

        List<Map<String, Object>> selected = new ArrayList<>(missedWords);
        selected.addAll(otherWords.subList(0, Math.min(count - missedWords.size(), otherWords.size())));
        Collections.shuffle(selected);

        String typeLabel = useTurkish ? "tr" : "eng";
        Random rnd = new Random();
        List<Map<String, Object>> questions = new ArrayList<>();

        for (int i = 0; i < selected.size(); i++) {
            Map<String, Object> word = selected.get(i);
            String estonian = (String) word.get("word_value");
            String english = (String) word.get("english");
            String nativeT = useTurkish && turkishLookup.containsKey(estonian) ? turkishLookup.get(estonian) : english;

            boolean isEstToNative = rnd.nextBoolean();
            String correct = isEstToNative ? nativeT : estonian;
            List<String> pool = allWords.stream()
                .filter(w -> !w.get("word_value").equals(estonian))
                .map(w -> {
                    String est = (String) w.get("word_value");
                    String eng = (String) w.get("english");
                    return isEstToNative ? (useTurkish && turkishLookup.containsKey(est) ? turkishLookup.get(est) : eng) : est;
                }).collect(Collectors.toList());
            Collections.shuffle(pool);
            List<String> distractors = pool.subList(0, Math.min(3, pool.size()));
            List<String> options = new ArrayList<>(distractors);
            options.add(correct);
            Collections.shuffle(options);

            questions.add(Map.of(
                "index", i,
                "type", isEstToNative ? "est-to-" + typeLabel : typeLabel + "-to-est",
                "prompt", isEstToNative ? estonian : nativeT,
                "options", options,
                "correctIndex", options.indexOf(correct),
                "word", Map.of("estonian", estonian, "english", english)));
        }

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

        quizRepo.saveQuizResult(chatId, score, total, answers);
        activityRepo.logQuizActivity(chatId);
        sentWordRepo.incrementQuizCount(chatId, answers.stream().map(QuizAnswer::estonian).toList());

        return ResponseEntity.ok(Map.of(
            "score", score,
            "total", total,
            "percentage", Math.round((float) score / total * 100)));
    }

    private Map<String, String> buildTurkishLookup() {
        return wordBankService.getAllWords().stream()
            .filter(w -> w.getTurkish() != null)
            .collect(Collectors.toMap(Word::getEstonian, Word::getTurkish, (a, b) -> a));
    }
}
