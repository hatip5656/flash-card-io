package io.flashcard.model;

import java.time.LocalTime;

/** A learner's daily goal choices: which tasks count, their targets, and when to notify. */
public record DailyGoalSettings(
    boolean wordsEnabled, int wordsTarget,
    boolean quizEnabled, int quizTarget,
    boolean reviewsEnabled, boolean grammarEnabled,
    boolean notifyEnabled, LocalTime morningTime, LocalTime eveningTime) {

    public static DailyGoalSettings defaults() {
        return new DailyGoalSettings(true, 10, true, 20, true, true, true, LocalTime.of(9, 0), LocalTime.of(20, 0));
    }

    /** Every task switched off: the daily goal is off. */
    public boolean allOff() {
        return !wordsEnabled && !quizEnabled && !reviewsEnabled && !grammarEnabled;
    }
}
