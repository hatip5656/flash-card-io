package io.flashcard.service;

import io.flashcard.config.AppProperties;
import io.flashcard.model.DailyGoalRow;
import io.flashcard.model.DailyGoalSettings;
import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.DailyGoalRepository;
import io.flashcard.repository.LearningRepository;
import io.flashcard.repository.SubscriberRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The learner's goal for today: new words, quiz questions, due reviews and one grammar lesson.
 * Targets are fixed when the day's row is created, so settings changes count from the next day,
 * while switching a task off counts at once. Days follow the app's timezone (Tallinn).
 */
@Service
public class DailyGoalService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DailyGoalService.class);

    public static final Set<Integer> WORD_TARGETS = Set.of(5, 10, 20);
    public static final Set<Integer> QUIZ_TARGETS = Set.of(10, 20, 40);
    static final int MAX_REVIEWS = 20;

    public record Task(String key, int target, int done, boolean enabled) {
        public boolean finished() { return done >= target; }
    }

    /** Today's goal. {@code tasks} holds only the tasks that count today. */
    public record Today(LocalDate date, List<Task> tasks, String grammarLessonId, String grammarTopic,
                        boolean complete, int streak) {}

    private final DailyGoalRepository repo;
    private final SubscriberRepository subscribers;
    private final GrammarBankService grammarBank;
    private final LearningRepository learning;
    private final LearningSettings settings;
    private final ActivityRepository activity;
    private final ZoneId zone;
    private final GoalNotifier notifier;
    private final Clock clock;

    public DailyGoalService(DailyGoalRepository repo, SubscriberRepository subscribers, GrammarBankService grammarBank,
                            LearningRepository learning, LearningSettings settings, ActivityRepository activity,
                            AppProperties props, GoalNotifier notifier, Clock clock) {
        this.repo = repo;
        this.subscribers = subscribers;
        this.grammarBank = grammarBank;
        this.learning = learning;
        this.settings = settings;
        this.activity = activity;
        this.zone = ZoneId.of(props.getCronTimezone());
        this.notifier = notifier;
        this.clock = clock;
    }

    // ── Time ──────────────────────────────────────

    public LocalDate localDate() { return LocalDate.now(clock.withZone(zone)); }

    public LocalTime localTime() { return LocalTime.now(clock.withZone(zone)); }

    public Instant dayStart(LocalDate date) { return date.atStartOfDay(zone).toInstant(); }

    // ── Goal ──────────────────────────────────────

    public Today today(long chatId) {
        DailyGoalSettings s = settings(chatId);
        LocalDate date = localDate();
        DailyGoalRow row = ensureRow(chatId, date, s);
        String level = subscribers.getSubscriberLevel(chatId);

        List<Task> tasks = new ArrayList<>();
        if (s.wordsEnabled()) {
            tasks.add(task("words", row.wordsTarget(), repo.wordsSeenSince(chatId, dayStart(date))));
        }
        if (s.quizEnabled()) tasks.add(task("quiz", row.quizTarget(), row.quizDone()));
        if (s.reviewsEnabled() && row.reviewsTarget() > 0) {
            tasks.add(task("reviews", row.reviewsTarget(), row.reviewsDone()));
        }
        String lessonId = s.grammarEnabled() ? row.grammarLessonId() : null;
        String topic = null;
        if (lessonId != null) {
            boolean practiced = level != null && repo.grammarPracticedSince(chatId, dayStart(date), level);
            tasks.add(task("grammar", 1, practiced ? 1 : 0));
            topic = topicOf(lessonId, subscribers.getPreferences(chatId).getNativeLanguage());
        }
        tasks.removeIf(t -> t.target() <= 0);
        boolean complete = !tasks.isEmpty() && tasks.stream().allMatch(Task::finished);
        return new Today(date, tasks, lessonId, topic, complete, streak(chatId, s, date));
    }

    /** Today's goal, recording it as completed (and sending "done") if it is. */
    public Today refresh(long chatId) {
        Today t = today(chatId);
        recordIfComplete(chatId, t);
        return t;
    }

    /**
     * Count quiz answers (and the due reviews among them). Call before the answers update review dates.
     * Never throws: the goal must not break the answer it is counting.
     */
    public void recordQuizAnswers(long chatId, List<String> estonian) {
        if (estonian == null || estonian.isEmpty()) return;
        try {
            LocalDate date = localDate();
            ensureRow(chatId, date, settings(chatId));
            int due = repo.countDue(chatId, date, estonian);
            repo.addQuiz(chatId, date, estonian.size(), due);
            checkCompletion(chatId);
        } catch (RuntimeException e) {
            log.warn("Daily goal quiz progress for {} failed: {}", chatId, e.getMessage());
        }
    }

    /** Words seen, grammar practised or exam answered: see whether that finished the goal. Never throws. */
    public void onProgress(long chatId) {
        try {
            checkCompletion(chatId);
        } catch (RuntimeException e) {
            log.warn("Daily goal check for {} failed: {}", chatId, e.getMessage());
        }
    }

    public int streak(long chatId) {
        return streak(chatId, settings(chatId), localDate());
    }

    // ── Settings ──────────────────────────────────

    public DailyGoalSettings settings(long chatId) {
        return repo.findSettings(chatId).orElseGet(DailyGoalSettings::defaults);
    }

    public DailyGoalSettings saveSettings(long chatId, DailyGoalSettings s) {
        if (!WORD_TARGETS.contains(s.wordsTarget())) throw new IllegalArgumentException("wordsTarget must be one of " + WORD_TARGETS);
        if (!QUIZ_TARGETS.contains(s.quizTarget())) throw new IllegalArgumentException("quizTarget must be one of " + QUIZ_TARGETS);
        if (s.morningTime() == null || s.eveningTime() == null) throw new IllegalArgumentException("times are required");
        repo.saveSettings(chatId, s);
        checkCompletion(chatId);
        return s;
    }

    // ── Internals ─────────────────────────────────

    private void checkCompletion(long chatId) {
        // Already recorded (and announced, or nothing to announce): skip the progress queries
        var row = repo.find(chatId, localDate());
        if (row.isPresent() && row.get().completedAt() != null
            && (row.get().doneSentAt() != null || !settings(chatId).notifyEnabled())) {
            return;
        }
        recordIfComplete(chatId, today(chatId));
    }

    private void recordIfComplete(long chatId, Today t) {
        if (!t.complete()) return;
        repo.claim(chatId, t.date(), "completed_at");
        if (settings(chatId).notifyEnabled() && repo.claim(chatId, t.date(), "done_sent_at")) {
            notifier.sendDone(chatId, streak(chatId));
        }
    }

    private DailyGoalRow ensureRow(long chatId, LocalDate date, DailyGoalSettings s) {
        return repo.find(chatId, date).orElseGet(() -> repo.insertIfAbsent(DailyGoalRow.create(chatId, date,
            s.wordsTarget(), s.quizTarget(),
            Math.min(MAX_REVIEWS, repo.reviewsDueOn(chatId, date)),
            nextGrammarLesson(chatId))));
    }

    /** The first lesson at the learner's level, in lesson order, that is not mastered yet. */
    private String nextGrammarLesson(long chatId) {
        String level = subscribers.getSubscriberLevel(chatId);
        if (level == null) return null;
        double masteredScore = settings.num("grammar.mastered_score");
        int masteredAttempts = settings.count("grammar.mastered_min_attempts");
        Map<String, Map<String, Object>> mastery = new HashMap<>();
        for (var m : learning.getAllMastery(chatId)) mastery.put((String) m.get("grammar_lesson_id"), m);
        for (GrammarLesson lesson : grammarBank.getAllLessons()) {
            if (!level.equalsIgnoreCase(lesson.cefrLevel())) continue;
            var m = mastery.get(lesson.id());
            boolean mastered = m != null
                && ((Number) m.get("mastery")).doubleValue() >= masteredScore
                && ((Number) m.get("attempts")).intValue() >= masteredAttempts;
            if (!mastered) return lesson.id();
        }
        return null;
    }

    private String topicOf(String lessonId, String lang) {
        return grammarBank.getAllLessons().stream().filter(l -> l.id().equals(lessonId))
            .map(l -> l.getTopic(lang)).findFirst().orElse(null);
    }

    private int streak(long chatId, DailyGoalSettings s, LocalDate date) {
        return s.allOff() ? activity.getStreak(chatId) : repo.completedStreak(chatId, date);
    }

    private static Task task(String key, int target, int done) {
        return new Task(key, target, Math.min(done, target), true);
    }
}
