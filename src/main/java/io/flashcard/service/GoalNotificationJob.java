package io.flashcard.service;

import io.flashcard.repository.DailyGoalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Every 5 minutes: the morning plan to learners whose morning time has come, and a reminder of what is
 * left to those whose evening time has come and who have not finished. Each message is claimed on the
 * goal row before sending, so overlapping runs never send it twice.
 */
@Service
public class GoalNotificationJob {

    private static final Logger log = LoggerFactory.getLogger(GoalNotificationJob.class);

    private final DailyGoalService goals;
    private final DailyGoalRepository repo;
    private final GoalNotifier notifier;

    public GoalNotificationJob(DailyGoalService goals, DailyGoalRepository repo, GoalNotifier notifier) {
        this.goals = goals;
        this.repo = repo;
        this.notifier = notifier;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void run() {
        LocalDate date = goals.localDate();
        LocalTime now = goals.localTime();
        for (long chatId : repo.learnersDueFor("morning_sent_at", "morning_time", date, now)) {
            each(chatId, () -> {
                var today = goals.refresh(chatId);
                // Nothing counts today (e.g. only reviews on, none due): no empty message
                if (today.tasks().isEmpty()) return;
                if (repo.claim(chatId, date, "morning_sent_at")) notifier.sendMorning(chatId, today);
            });
        }
        for (long chatId : repo.learnersDueFor("evening_sent_at", "evening_time", date, now)) {
            each(chatId, () -> {
                var today = goals.refresh(chatId);
                if (today.tasks().isEmpty() || today.complete()) return;
                if (repo.claim(chatId, date, "evening_sent_at")) notifier.sendEvening(chatId, today);
            });
        }
    }

    private void each(long chatId, Runnable work) {
        try {
            if (goals.settings(chatId).allOff()) return;
            work.run();
        } catch (Exception e) {
            log.warn("Daily goal notification for {} failed: {}", chatId, e.getMessage());
        }
    }
}
