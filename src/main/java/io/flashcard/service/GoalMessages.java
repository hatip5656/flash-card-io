package io.flashcard.service;

import io.flashcard.service.DailyGoalService.Task;
import io.flashcard.service.DailyGoalService.Today;

import java.util.List;
import java.util.function.ToIntFunction;

/** Notification texts for the daily goal, in the learner's language (Turkish unless English). */
final class GoalMessages {

    static final String TITLE = "Wordagram";

    private GoalMessages() {}

    static String morning(Today t, String lang) {
        return (english(lang) ? "Today's goal: " : "Bugünkü hedefin: ") + parts(t, t.tasks(), Task::target, lang);
    }

    /** Only the unfinished tasks, with what is left of each. */
    static String evening(Today t, String lang) {
        List<Task> open = t.tasks().stream().filter(x -> !x.finished()).toList();
        return (english(lang) ? "Almost there: " : "Hedefe az kaldı: ") + parts(t, open, x -> x.target() - x.done(), lang);
    }

    static String done(int streak, String lang) {
        return english(lang) ? "Daily goal done 🔥 " + streak + "-day streak" : "Günlük hedef tamam 🔥 " + streak + " günlük seri";
    }

    private static String parts(Today t, List<Task> tasks, ToIntFunction<Task> count, String lang) {
        boolean en = english(lang);
        return String.join(" · ", tasks.stream().map(task -> {
            int n = count.applyAsInt(task);
            return switch (task.key()) {
                case "words" -> n + (en ? " words" : " kelime");
                case "quiz" -> n + (en ? " quiz questions" : " quiz sorusu");
                case "reviews" -> n + (en ? " reviews" : " tekrar");
                case "grammar" -> (en ? "1 grammar" : "1 gramer") + (t.grammarTopic() != null ? " (" + t.grammarTopic() + ")" : "");
                default -> n + " " + task.key();
            };
        }).toList());
    }

    private static boolean english(String lang) {
        return "english".equalsIgnoreCase(lang);
    }
}
