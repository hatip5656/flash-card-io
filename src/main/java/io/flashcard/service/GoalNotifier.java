package io.flashcard.service;

/** Delivers the daily goal messages to a learner's devices. */
public interface GoalNotifier {

    void sendMorning(long chatId, DailyGoalService.Today today);

    void sendEvening(long chatId, DailyGoalService.Today today);

    void sendDone(long chatId, int streak);
}
