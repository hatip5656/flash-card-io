package io.flashcard.service;

import io.flashcard.config.AppProperties;
import io.flashcard.model.DailyGoalRow;
import io.flashcard.model.DailyGoalSettings;
import io.flashcard.model.GrammarLesson;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.DailyGoalRepository;
import io.flashcard.repository.LearningRepository;
import io.flashcard.repository.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyGoalServiceTest {

    private static final long USER = 42L;
    // 10:00 in Tallinn (EET, UTC+2)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final Instant DAY_START = Instant.parse("2026-11-05T22:00:00Z");

    private DailyGoalRepository repo;
    private SubscriberRepository subscribers;
    private GrammarBankService grammarBank;
    private LearningRepository learning;
    private ActivityRepository activity;
    private GoalNotifier notifier;
    private DailyGoalService service;

    @BeforeEach
    void setUp() {
        repo = mock(DailyGoalRepository.class);
        subscribers = mock(SubscriberRepository.class);
        grammarBank = mock(GrammarBankService.class);
        learning = mock(LearningRepository.class);
        activity = mock(ActivityRepository.class);
        notifier = mock(GoalNotifier.class);
        AppProperties props = new AppProperties();
        props.setCronTimezone("Europe/Tallinn");
        var settings = LearningSettings.of(Map.of("grammar.mastered_score", 0.8, "grammar.mastered_min_attempts", 3));
        service = new DailyGoalService(repo, subscribers, grammarBank, learning, settings, activity, props, notifier, CLOCK);

        when(subscribers.getSubscriberLevel(USER)).thenReturn("A2");
        when(subscribers.getPreferences(USER)).thenReturn(new UserPreferences());
        when(grammarBank.getAllLessons()).thenReturn(List.of(
            lesson("a1-x", "A1", "Tervitused"),
            lesson("a2-pron", "A2", "Personal pronouns"),
            lesson("a2-past", "A2", "Past tense")));
        when(learning.getAllMastery(USER)).thenReturn(List.of());
        when(repo.findSettings(USER)).thenReturn(Optional.empty());
        when(repo.insertIfAbsent(any())).thenAnswer(inv -> stored(inv.getArgument(0)));
        // Saved settings are read back, as the real repository does
        doAnswer(inv -> {
            givenSettings(inv.getArgument(1));
            return null;
        }).when(repo).saveSettings(eq(USER), any());
    }

    // ── Creating the day's goal ──

    @Test
    void createsGoalWithDefaultsAndFixedTargets() {
        when(repo.find(USER, TODAY)).thenReturn(Optional.empty());
        when(repo.reviewsDueOn(USER, TODAY)).thenReturn(35);

        var today = service.today(USER);

        var row = ArgumentCaptor.forClass(DailyGoalRow.class);
        verify(repo).insertIfAbsent(row.capture());
        assertEquals(TODAY, row.getValue().date());
        assertEquals(10, row.getValue().wordsTarget());
        assertEquals(20, row.getValue().quizTarget());
        assertEquals(20, row.getValue().reviewsTarget(), "reviews capped at 20");
        assertEquals("a2-pron", row.getValue().grammarLessonId(), "first unmastered lesson at the learner's level");
        assertEquals(List.of("words", "quiz", "reviews", "grammar"), today.tasks().stream().map(DailyGoalService.Task::key).toList());
        assertEquals("Kişi Zamirleri", today.grammarTopic(), "Turkish learners see the Turkish topic");
    }

    @Test
    void createsGoalOnFirstProgress() {
        when(repo.find(USER, TODAY)).thenReturn(Optional.empty());
        when(repo.reviewsDueOn(USER, TODAY)).thenReturn(5);

        service.recordQuizAnswers(USER, List.of("maja"));

        var row = ArgumentCaptor.forClass(DailyGoalRow.class);
        verify(repo, atLeastOnce()).insertIfAbsent(row.capture());
        assertEquals(10, row.getValue().wordsTarget());
        assertEquals(5, row.getValue().reviewsTarget());
        assertEquals("a2-pron", row.getValue().grammarLessonId());
    }

    @Test
    void reviewsTargetFixedAfterCreation() {
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 12, "a2-pron"));
        when(repo.reviewsDueOn(USER, TODAY)).thenReturn(3);

        var reviews = task(service.today(USER), "reviews");

        assertEquals(12, reviews.target());
        verify(repo, never()).insertIfAbsent(any());
    }

    @Test
    void noGrammarTaskWhenLevelMastered() {
        when(repo.find(USER, TODAY)).thenReturn(Optional.empty());
        when(learning.getAllMastery(USER)).thenReturn(List.of(
            mastery("a2-pron", 0.9, 4), mastery("a2-past", 0.85, 3)));

        var today = service.today(USER);

        assertNull(today.grammarLessonId());
        assertTrue(today.tasks().stream().noneMatch(t -> t.key().equals("grammar")));
    }

    @Test
    void weakLessonIsNotTreatedAsMastered() {
        when(repo.find(USER, TODAY)).thenReturn(Optional.empty());
        when(learning.getAllMastery(USER)).thenReturn(List.of(
            mastery("a2-pron", 0.9, 2), mastery("a2-past", 0.9, 5)));

        assertEquals("a2-pron", service.today(USER).grammarLessonId(), "too few attempts to count as mastered");
    }

    // ── Progress ──

    @Test
    void wordsProgressFromSentWords() {
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 0, null));
        when(repo.wordsSeenSince(USER, DAY_START)).thenReturn(4);

        var words = task(service.today(USER), "words");

        assertEquals(4, words.done());
        assertEquals(10, words.target());
    }

    @Test
    void quizAnswerCountsDueReviews() {
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 5, null));
        when(repo.countDue(USER, TODAY, List.of("maja", "koer", "kass"))).thenReturn(2);

        service.recordQuizAnswers(USER, List.of("maja", "koer", "kass"));

        verify(repo).addQuiz(USER, TODAY, 3, 2);
    }

    @Test
    void disabledTaskHiddenAndIgnoredForCompletion() {
        givenSettings(new DailyGoalSettings(true, 10, false, 20, true, true, true, LocalTime.of(9, 0), LocalTime.of(20, 0)));
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 0, null));
        when(repo.wordsSeenSince(USER, DAY_START)).thenReturn(10);

        var today = service.today(USER);

        assertEquals(List.of("words"), today.tasks().stream().map(DailyGoalService.Task::key).toList());
        assertTrue(today.complete());
    }

    // ── Completion and the done message ──

    @Test
    void disablingLastOpenTaskCompletesGoal() {
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 0, "a2-pron"));
        when(repo.wordsSeenSince(USER, DAY_START)).thenReturn(10);
        when(repo.claim(eq(USER), eq(TODAY), anyString())).thenReturn(true);
        when(repo.completedStreak(USER, TODAY)).thenReturn(3);

        service.saveSettings(USER, new DailyGoalSettings(true, 10, false, 20, true, false, true,
            LocalTime.of(9, 0), LocalTime.of(20, 0)));

        verify(repo).claim(USER, TODAY, "completed_at");
        verify(notifier).sendDone(USER, 3);
    }

    @Test
    void doneSentOnlyOnce() {
        givenSettings(new DailyGoalSettings(true, 10, false, 20, false, false, true, LocalTime.of(9, 0), LocalTime.of(20, 0)));
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 0, null));
        when(repo.wordsSeenSince(USER, DAY_START)).thenReturn(10);
        when(repo.claim(USER, TODAY, "completed_at")).thenReturn(false);
        when(repo.claim(USER, TODAY, "done_sent_at")).thenReturn(false);

        service.onProgress(USER);

        verify(notifier, never()).sendDone(anyLong(), anyInt());
    }

    @Test
    void noDoneMessageWhenNotificationsOff() {
        givenSettings(new DailyGoalSettings(true, 10, false, 20, false, false, false, LocalTime.of(9, 0), LocalTime.of(20, 0)));
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 0, null));
        when(repo.wordsSeenSince(USER, DAY_START)).thenReturn(10);
        when(repo.claim(eq(USER), eq(TODAY), anyString())).thenReturn(true);

        service.onProgress(USER);

        verify(repo).claim(USER, TODAY, "completed_at");
        verify(notifier, never()).sendDone(anyLong(), anyInt());
    }

    @Test
    void incompleteGoalNotClaimed() {
        givenRow(DailyGoalRow.create(USER, TODAY, 10, 20, 0, null));
        when(repo.wordsSeenSince(USER, DAY_START)).thenReturn(9);

        service.onProgress(USER);

        verify(repo, never()).claim(anyLong(), any(), anyString());
    }

    // ── Streak and settings ──

    @Test
    void streakCountsCompletedDays() {
        when(repo.completedStreak(USER, TODAY)).thenReturn(5);
        assertEquals(5, service.streak(USER));
    }

    @Test
    void streakFallsBackWhenAllTasksOff() {
        givenSettings(new DailyGoalSettings(false, 10, false, 20, false, false, true, LocalTime.of(9, 0), LocalTime.of(20, 0)));
        when(activity.getStreak(USER)).thenReturn(7);

        assertEquals(7, service.streak(USER));
    }

    @Test
    void rejectsUnknownTargets() {
        assertThrows(IllegalArgumentException.class, () -> service.saveSettings(USER,
            new DailyGoalSettings(true, 7, true, 20, true, true, true, LocalTime.of(9, 0), LocalTime.of(20, 0))));
        assertThrows(IllegalArgumentException.class, () -> service.saveSettings(USER,
            new DailyGoalSettings(true, 10, true, 25, true, true, true, LocalTime.of(9, 0), LocalTime.of(20, 0))));
        verify(repo, never()).saveSettings(anyLong(), any());
    }

    // ── helpers ──

    private void givenRow(DailyGoalRow row) {
        when(repo.find(USER, TODAY)).thenReturn(Optional.of(row));
    }

    private void givenSettings(DailyGoalSettings s) {
        when(repo.findSettings(USER)).thenReturn(Optional.of(s));
    }

    private static DailyGoalRow stored(DailyGoalRow r) {
        return r;
    }

    private static DailyGoalService.Task task(DailyGoalService.Today t, String key) {
        return t.tasks().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
    }

    private static GrammarLesson lesson(String id, String level, String topic) {
        String tr = id.equals("a2-pron") ? "Kişi Zamirleri" : null;
        return new GrammarLesson(id, level, topic, tr, "", "");
    }

    private static Map<String, Object> mastery(String lessonId, double score, int attempts) {
        return Map.of("grammar_lesson_id", lessonId, "mastery", score, "attempts", attempts);
    }
}
