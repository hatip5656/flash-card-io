package io.flashcard.service;

import io.flashcard.model.DailyGoalSettings;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.DailyGoalRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.DailyGoalService.Task;
import io.flashcard.service.DailyGoalService.Today;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoalNotificationJobTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    private static final LocalTime NOW = LocalTime.of(9, 5);

    private DailyGoalService goals;
    private DailyGoalRepository repo;
    private GoalNotifier notifier;
    private GoalNotificationJob job;

    private final Today open = new Today(DAY, List.of(new Task("words", 10, 2, true)), null, null, false, 0);
    private final Today done = new Today(DAY, List.of(new Task("words", 10, 10, true)), null, null, true, 1);

    @BeforeEach
    void setUp() {
        goals = mock(DailyGoalService.class);
        repo = mock(DailyGoalRepository.class);
        notifier = mock(GoalNotifier.class);
        job = new GoalNotificationJob(goals, repo, notifier);
        when(goals.localDate()).thenReturn(DAY);
        when(goals.localTime()).thenReturn(NOW);
        when(goals.settings(anyLong())).thenReturn(DailyGoalSettings.defaults());
        when(repo.learnersDueFor(anyString(), anyString(), any(), any())).thenReturn(List.of());
    }

    @Test
    void sendsMorningOnceClaimed() {
        when(repo.learnersDueFor("morning_sent_at", "morning_time", DAY, NOW)).thenReturn(List.of(1L));
        when(goals.today(1L)).thenReturn(open);
        when(repo.claim(1L, DAY, "morning_sent_at")).thenReturn(true);

        job.run();

        verify(notifier).sendMorning(1L, open);
    }

    @Test
    void claimFailsSecondTime_noSecondSend() {
        when(repo.learnersDueFor("morning_sent_at", "morning_time", DAY, NOW)).thenReturn(List.of(1L));
        when(goals.today(1L)).thenReturn(open);
        when(repo.claim(1L, DAY, "morning_sent_at")).thenReturn(true, false);

        job.run();
        job.run();

        verify(notifier, times(1)).sendMorning(1L, open);
    }

    @Test
    void eveningSkippedWhenComplete() {
        when(repo.learnersDueFor("evening_sent_at", "evening_time", DAY, NOW)).thenReturn(List.of(2L));
        when(goals.today(2L)).thenReturn(done);

        job.run();

        verify(repo, never()).claim(2L, DAY, "evening_sent_at");
        verify(notifier, never()).sendEvening(anyLong(), any());
    }

    @Test
    void eveningSentWhenOpen() {
        when(repo.learnersDueFor("evening_sent_at", "evening_time", DAY, NOW)).thenReturn(List.of(2L));
        when(goals.today(2L)).thenReturn(open);
        when(repo.claim(2L, DAY, "evening_sent_at")).thenReturn(true);

        job.run();

        verify(notifier).sendEvening(2L, open);
    }

    @Test
    void goalOffGetsNoMessages() {
        when(repo.learnersDueFor("morning_sent_at", "morning_time", DAY, NOW)).thenReturn(List.of(3L));
        when(goals.settings(3L)).thenReturn(new DailyGoalSettings(false, 10, false, 20, false, false, true,
            LocalTime.of(9, 0), LocalTime.of(20, 0)));

        job.run();

        verify(notifier, never()).sendMorning(anyLong(), any());
    }

    @Test
    void oneLearnerFailureDoesNotStopOthers() {
        when(repo.learnersDueFor("morning_sent_at", "morning_time", DAY, NOW)).thenReturn(List.of(1L, 2L));
        when(goals.today(1L)).thenThrow(new RuntimeException("boom"));
        when(goals.today(2L)).thenReturn(open);
        when(repo.claim(2L, DAY, "morning_sent_at")).thenReturn(true);

        job.run();

        verify(notifier).sendMorning(2L, open);
    }

    @Test
    void deadTokenRemovedOthersStillSent() {
        FcmClient fcm = mock(FcmClient.class);
        SubscriberRepository subscribers = mock(SubscriberRepository.class);
        when(fcm.isConfigured()).thenReturn(true);
        when(subscribers.getPreferences(7L)).thenReturn(new UserPreferences());
        when(repo.deviceTokens(7L)).thenReturn(List.of("dead", "alive"));
        when(fcm.send(eq("dead"), anyString(), anyString())).thenReturn(FcmClient.SendResult.DEAD_TOKEN);
        when(fcm.send(eq("alive"), anyString(), anyString())).thenReturn(FcmClient.SendResult.SENT);

        new FcmGoalNotifier(fcm, repo, subscribers).sendDone(7L, 3);

        verify(repo).deleteDevice("dead");
        verify(fcm).send("alive", "Wordagram", "Günlük hedef tamam 🔥 3 günlük seri");
        verify(repo, never()).deleteDevice("alive");
    }
}
