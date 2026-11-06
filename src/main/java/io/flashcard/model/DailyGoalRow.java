package io.flashcard.model;

import java.time.Instant;
import java.time.LocalDate;

/** One learner's goal for one day: targets fixed at creation, quiz/review counters, send markers. */
public record DailyGoalRow(
    long chatId, LocalDate date,
    int wordsTarget, int quizTarget, int reviewsTarget, String grammarLessonId,
    int quizDone, int reviewsDone,
    Instant completedAt, Instant morningSentAt, Instant eveningSentAt, Instant doneSentAt) {

    public static DailyGoalRow create(long chatId, LocalDate date, int wordsTarget, int quizTarget,
                                      int reviewsTarget, String grammarLessonId) {
        return new DailyGoalRow(chatId, date, wordsTarget, quizTarget, reviewsTarget, grammarLessonId,
            0, 0, null, null, null, null);
    }
}
