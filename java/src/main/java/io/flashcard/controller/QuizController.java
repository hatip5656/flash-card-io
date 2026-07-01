package io.flashcard.controller;

import io.flashcard.model.QuizAnswer;
import io.flashcard.model.QuizResult;
import io.flashcard.model.QuizSession;
import io.flashcard.model.UserPreferences;
import io.flashcard.model.Word;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizRepository quizRepo;
    private final SentWordRepository sentWordRepo;
    private final SubscriberRepository subscriberRepo;
    private final WordBankService wordBankService;

    public QuizController(QuizRepository quizRepo, SentWordRepository sentWordRepo,
                          SubscriberRepository subscriberRepo, WordBankService wordBankService) {
        this.quizRepo = quizRepo;
        this.sentWordRepo = sentWordRepo;
        this.subscriberRepo = subscriberRepo;
        this.wordBankService = wordBankService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startQuiz(HttpServletRequest request,
                                       @RequestParam(defaultValue = "5") int count) {
        long chatId = getUserId(request);
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        boolean useTurkish = "turkish".equals(prefs.getNativeLanguage());

        Map<String, String> turkishLookup = useTurkish ? buildTurkishLookup() : Map.of();
        List<Map<String, Object>> allWords = sentWordRepo.getLearnedWordsForQuiz(chatId);

        if (allWords.size() < 4) {
            String msg = useTurkish
                ? "Quiz baslatmak icin en az 4 kelime ogrenmelisin. Ogrenmeye devam et!"
                : "Need at least 4 learned words to start a quiz. Keep learning!";
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }

        count = Math.min(Math.max(count, 5), 50);
        List<Map<String, Object>> missed = quizRepo.getMostMissedWords(chatId, Math.min(count, 20));
        Set<String> missedSet = missed.stream().map(m -> (String) m.get("estonian")).collect(Collectors.toSet());

        List<Map<String, Object>> prioritized = new ArrayList<>();
        prioritized.addAll(allWords.stream().filter(w -> missedSet.contains(w.get("word_value"))).toList());
        prioritized.addAll(allWords.stream().filter(w -> !missedSet.contains(w.get("word_value"))).toList());
        List<Map<String, Object>> selected = prioritized.subList(0, Math.min(count, prioritized.size()));

        String typeLabel = useTurkish ? "tr" : "eng";
        List<QuizSession.QuizQuestion> questions = new ArrayList<>();
        List<QuizSession.QuizWord> words = new ArrayList<>();
        Random rnd = new Random();

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

            questions.add(new QuizSession.QuizQuestion(
                i,
                isEstToNative ? "est-to-" + typeLabel : typeLabel + "-to-est",
                isEstToNative ? estonian : nativeT,
                options,
                options.indexOf(correct)));
            words.add(new QuizSession.QuizWord(estonian, english));
        }

        quizRepo.upsertQuizSession(chatId, questions, words);

        return ResponseEntity.ok(Map.of(
            "totalQuestions", questions.size(),
            "question", Map.of(
                "index", questions.get(0).index(),
                "type", questions.get(0).type(),
                "prompt", questions.get(0).prompt(),
                "options", questions.get(0).options())));
    }

    @PostMapping("/answer")
    public ResponseEntity<?> submitAnswer(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        QuizSession session = quizRepo.getQuizSession(chatId);
        if (session == null) {
            return ResponseEntity.status(404).body(Map.of("error", "No active quiz session. Start one with POST /api/quiz/start"));
        }

        List<QuizSession.QuizQuestion> questions = session.getQuestions();
        List<QuizSession.AnswerEntry> answers = session.getAnswers();
        int qIndex = answers.size();
        if (qIndex >= questions.size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Quiz already complete"));
        }

        QuizSession.QuizQuestion question = questions.get(qIndex);
        Object chosenObj = body.get("chosenIndex");
        if (chosenObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "chosenIndex must be an integer between 0 and " + (question.options().size() - 1)));
        }
        int chosenIndex = ((Number) chosenObj).intValue();
        if (chosenIndex < 0 || chosenIndex >= question.options().size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "chosenIndex must be an integer between 0 and " + (question.options().size() - 1)));
        }

        boolean correct = chosenIndex == question.correctIndex();
        QuizSession.AnswerEntry newAnswer = new QuizSession.AnswerEntry(chosenIndex, correct);
        quizRepo.pushQuizAnswer(chatId, newAnswer);

        List<QuizSession.AnswerEntry> allAnswers = new ArrayList<>(answers);
        allAnswers.add(newAnswer);
        boolean isComplete = allAnswers.size() >= questions.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("correct", correct);
        result.put("correctAnswer", question.options().get(question.correctIndex()));
        result.put("chosenAnswer", question.options().get(chosenIndex));

        if (isComplete) {
            int score = (int) allAnswers.stream().filter(QuizSession.AnswerEntry::correct).count();
            int total = questions.size();
            List<QuizSession.QuizWord> words = session.getWords();
            List<QuizAnswer> quizAnswers = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                QuizSession.QuizQuestion q = questions.get(i);
                QuizSession.AnswerEntry a = allAnswers.get(i);
                quizAnswers.add(new QuizAnswer(
                    words.get(i).estonian(),
                    q.options().get(q.correctIndex()),
                    q.options().get(a.chosen()),
                    a.correct()));
            }

            quizRepo.saveQuizResult(chatId, score, total, quizAnswers);
            sentWordRepo.incrementQuizCount(chatId, words.stream().map(QuizSession.QuizWord::estonian).toList());
            quizRepo.deleteQuizSession(chatId);

            result.put("complete", true);
            result.put("score", score);
            result.put("total", total);
            result.put("percentage", Math.round((float) score / total * 100));
            result.put("results", quizAnswers);
        } else {
            QuizSession.QuizQuestion next = questions.get(qIndex + 1);
            result.put("complete", false);
            result.put("nextQuestion", Map.of(
                "index", next.index(),
                "type", next.type(),
                "prompt", next.prompt(),
                "options", next.options()));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public List<QuizResult> getHistory(HttpServletRequest request,
                                       @RequestParam(defaultValue = "5") int limit) {
        return quizRepo.getQuizHistory(getUserId(request), Math.min(limit, 20));
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(HttpServletRequest request) {
        long chatId = getUserId(request);
        Map<String, Object> raw = quizRepo.getQuizStats(chatId);
        int total = ((Number) raw.get("total")).intValue();
        int avgPct = (int) Math.round(((Number) raw.get("avg_pct")).doubleValue());
        Integer recentTrend = null;
        if (total >= 4 && raw.get("older_avg") != null) {
            recentTrend = (int) Math.round(((Number) raw.get("recent_avg")).doubleValue() - ((Number) raw.get("older_avg")).doubleValue());
        }
        return Map.of("totalQuizzes", total, "avgPercentage", avgPct, "recentTrend", recentTrend != null ? recentTrend : (Object) null);
    }

    @GetMapping("/missed")
    public List<Map<String, Object>> getMissedWords(HttpServletRequest request,
                                                     @RequestParam(defaultValue = "20") int limit) {
        return quizRepo.getMostMissedWords(getUserId(request), Math.min(limit, 50));
    }

    private Map<String, String> buildTurkishLookup() {
        return wordBankService.getAllWords().stream()
            .filter(w -> w.getTurkish() != null)
            .collect(Collectors.toMap(Word::getEstonian, Word::getTurkish, (a, b) -> a));
    }
}
