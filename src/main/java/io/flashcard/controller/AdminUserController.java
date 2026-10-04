package io.flashcard.controller;

import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.PodcastRepository;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.WordBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    public AdminUserController(SubscriberRepository subscriberRepo, SentWordRepository sentWordRepo,
                               QuizRepository quizRepo, ActivityRepository activityRepo,
                               WordBankService wordBankService, JdbcTemplate jdbc) {
        this.subscriberRepo = subscriberRepo;
        this.sentWordRepo = sentWordRepo;
        this.quizRepo = quizRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
        this.jdbc = jdbc;
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

    @GetMapping("/{chatId}/export")
    public ResponseEntity<?> exportUserContext(@PathVariable long chatId) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        if (level == null) return ResponseEntity.notFound().build();

        var prefs = subscriberRepo.getPreferences(chatId);
        String nativeLang = prefs != null ? prefs.getNativeLanguage() : "english";
        int streak = activityRepo.getStreak(chatId);
        var wordCounts = sentWordRepo.getWordCounts(chatId);
        var quizStats = quizRepo.getQuizStats(chatId);
        var readiness = sentWordRepo.getLevelReadiness(chatId, level);
        int totalForLevel = wordBankService.getWordsForLevel(level).size();

        var weakWords = sentWordRepo.getWeakWords(chatId, 20);
        var missedWords = quizRepo.getMostMissedWords(chatId, 20);
        var vocabulary = sentWordRepo.getVocabularyCollection(chatId);

        // Build both structured data and a text summary for AI
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("chatId", chatId);
        export.put("level", level);
        export.put("nativeLanguage", nativeLang);
        export.put("streak", streak);
        export.put("words", wordCounts);
        export.put("quiz", quizStats);
        export.put("readiness", readiness);
        export.put("totalWordsForLevel", totalForLevel);
        export.put("weakWords", weakWords);
        export.put("missedWords", missedWords);
        export.put("recentVocabulary", vocabulary.stream().limit(30).toList());
        export.put("totalVocabulary", vocabulary.size());

        // Text summary for direct AI pasting
        StringBuilder text = new StringBuilder();
        text.append("LEARNER PROFILE\n");
        text.append("CEFR Level: ").append(level).append("\n");
        text.append("Native language: ").append(nativeLang).append("\n");
        text.append("Streak: ").append(streak).append(" days\n");
        text.append("Vocabulary: ").append(wordCounts.get("seen")).append(" seen, ")
            .append(wordCounts.get("mastered")).append(" mastered\n");
        text.append("Level progress: ").append(readiness.get("strong")).append("/")
            .append(totalForLevel).append(" strong words\n");
        text.append("Quiz performance: ").append(quizStats.get("total")).append(" quizzes, avg ")
            .append(quizStats.get("avg_pct")).append("%\n\n");

        if (!weakWords.isEmpty()) {
            text.append("WEAK WORDS (low ease factor, prioritize these):\n");
            for (var w : weakWords) {
                text.append("- \"").append(w.get("word_value")).append("\" (")
                    .append(w.get("english")).append(", ease=")
                    .append(String.format("%.1f", ((Number) w.get("ease_factor")).doubleValue()))
                    .append(")\n");
            }
            text.append("\n");
        }

        if (!missedWords.isEmpty()) {
            text.append("MOST MISSED IN QUIZZES:\n");
            for (var w : missedWords) {
                text.append("- \"").append(w.get("estonian")).append("\" (missed ")
                    .append(w.get("mistakes")).append("x)\n");
            }
            text.append("\n");
        }

        text.append("RECENTLY LEARNED (latest 30):\n");
        vocabulary.stream().limit(30).forEach(w ->
            text.append("- \"").append(w.get("word_value")).append("\" (")
                .append(w.get("english")).append(")\n")
        );

        export.put("textSummary", text.toString());
        return ResponseEntity.ok(export);
    }

    @PatchMapping("/{chatId}/level")
    public ResponseEntity<?> changeLevel(@PathVariable long chatId, @RequestBody Map<String, String> body) {
        String level = body.get("level");
        if (level == null) return ResponseEntity.badRequest().body(Map.of("error", "Level required"));
        subscriberRepo.setSubscriberLevel(chatId, level);
        return ResponseEntity.ok(Map.of("chatId", chatId, "level", level));
    }

    @GetMapping("/{chatId}/activity")
    public ResponseEntity<?> getActivity(@PathVariable long chatId,
                                         @RequestParam(defaultValue = "30") int days) {
        var activity = jdbc.queryForList("""
            SELECT activity_date, words_learned, quizzes_taken, games_played,
                   phrases_seen, stories_read, dialogs_completed
            FROM activity_log WHERE chat_id = ?
            ORDER BY activity_date DESC LIMIT ?
            """, chatId, days);
        return ResponseEntity.ok(activity);
    }

    @GetMapping("/{chatId}/quiz-history")
    public ResponseEntity<?> getQuizHistory(@PathVariable long chatId,
                                            @RequestParam(defaultValue = "20") int limit) {
        var quizzes = jdbc.queryForList("""
            SELECT id, score, total, percentage, completed_at
            FROM quiz_results WHERE chat_id = ?
            ORDER BY completed_at DESC LIMIT ?
            """, chatId, limit);

        // Also get individual answers for latest quizzes
        for (var quiz : quizzes) {
            var answers = jdbc.queryForList("""
                SELECT estonian, correct_answer, user_answer, is_correct
                FROM quiz_answers WHERE quiz_id = ?
                ORDER BY id
                """, quiz.get("id"));
            quiz.put("answers", answers);
        }

        return ResponseEntity.ok(quizzes);
    }

    @GetMapping("/{chatId}/words")
    public ResponseEntity<?> getWordProgress(@PathVariable long chatId,
                                             @RequestParam(defaultValue = "50") int limit,
                                             @RequestParam(defaultValue = "0") int offset) {
        var words = jdbc.queryForList("""
            SELECT sw.word_id, sw.word_value, sw.english, sw.ease_factor,
                   sw.interval_days, sw.next_review, sw.repetitions,
                   sw.quiz_count, sw.seen_count, sw.mastered,
                   sw.last_quizzed_at, w.cefr_level
            FROM sent_words sw
            LEFT JOIN words w ON w.id = sw.word_id
            WHERE sw.chat_id = ?
            ORDER BY sw.ease_factor ASC
            LIMIT ? OFFSET ?
            """, chatId, limit, offset);
        int total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sent_words WHERE chat_id = ?", Integer.class, chatId);
        return ResponseEntity.ok(Map.of("items", words, "total", total));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<?> deleteUser(@PathVariable long chatId) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        if (level == null) return ResponseEntity.notFound().build();

        // Cascade delete all user data
        jdbc.update("DELETE FROM quiz_answers WHERE quiz_id IN (SELECT id FROM quiz_results WHERE chat_id = ?)", chatId);
        jdbc.update("DELETE FROM quiz_results WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM sent_words WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM sent_grammar WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM saved_words WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM activity_log WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM podcasts WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM word_comments WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM subscribers WHERE chat_id = ?", chatId);

        return ResponseEntity.ok(Map.of("deleted", chatId));
    }

    @PostMapping("/{chatId}/reset")
    public ResponseEntity<?> resetProgress(@PathVariable long chatId) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        if (level == null) return ResponseEntity.notFound().build();

        jdbc.update("DELETE FROM quiz_answers WHERE quiz_id IN (SELECT id FROM quiz_results WHERE chat_id = ?)", chatId);
        jdbc.update("DELETE FROM quiz_results WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM sent_words WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM sent_grammar WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM activity_log WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM podcasts WHERE chat_id = ?", chatId);

        return ResponseEntity.ok(Map.of("reset", chatId));
    }
}
