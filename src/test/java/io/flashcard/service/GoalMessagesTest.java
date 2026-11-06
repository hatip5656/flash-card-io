package io.flashcard.service;

import io.flashcard.service.DailyGoalService.Task;
import io.flashcard.service.DailyGoalService.Today;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalMessagesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    private static Today today(List<Task> tasks) {
        return new Today(DAY, tasks, "a2-pron", "Kişi Zamirleri", false, 0);
    }

    @Test
    void turkishMorningListsEveryTask() {
        var t = today(List.of(new Task("words", 10, 0, true), new Task("quiz", 20, 0, true),
            new Task("reviews", 12, 0, true), new Task("grammar", 1, 0, true)));

        assertEquals("Bugünkü hedefin: 10 kelime · 20 quiz sorusu · 12 tekrar · 1 gramer (Kişi Zamirleri)",
            GoalMessages.morning(t, "turkish"));
    }

    @Test
    void englishMorning() {
        var t = today(List.of(new Task("words", 5, 0, true), new Task("quiz", 10, 0, true)));

        assertEquals("Today's goal: 5 words · 10 quiz questions", GoalMessages.morning(t, "english"));
    }

    @Test
    void eveningListsOnlyWhatIsLeft() {
        var t = today(List.of(new Task("words", 10, 4, true), new Task("quiz", 20, 20, true),
            new Task("grammar", 1, 0, true)));

        assertEquals("Hedefe az kaldı: 6 kelime · 1 gramer (Kişi Zamirleri)", GoalMessages.evening(t, "turkish"));
    }

    @Test
    void doneMessages() {
        assertEquals("Günlük hedef tamam 🔥 5 günlük seri", GoalMessages.done(5, "turkish"));
        assertEquals("Daily goal done 🔥 5-day streak", GoalMessages.done(5, "english"));
    }
}
