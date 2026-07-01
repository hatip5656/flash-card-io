package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class ActivityRepository {

    private final JdbcTemplate jdbc;

    public ActivityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int logWordActivity(long chatId) {
        jdbc.update(
            """
            INSERT INTO activity_log (chat_id, words_learned) VALUES (?, 1)
            ON CONFLICT (chat_id, activity_date) DO UPDATE SET words_learned = activity_log.words_learned + 1
            """,
            chatId);
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sent_words WHERE chat_id = ?", Integer.class, chatId);
        return total != null ? total : 0;
    }

    public void logQuizActivity(long chatId) {
        jdbc.update(
            """
            INSERT INTO activity_log (chat_id, quizzes_taken) VALUES (?, 1)
            ON CONFLICT (chat_id, activity_date) DO UPDATE SET quizzes_taken = activity_log.quizzes_taken + 1
            """,
            chatId);
    }

    public int getStreak(long chatId) {
        Integer streak = jdbc.queryForObject(
            """
            SELECT COUNT(*) AS streak FROM (
              SELECT activity_date,
                     CURRENT_DATE - activity_date - ROW_NUMBER() OVER (ORDER BY activity_date DESC) + 1 AS grp
              FROM activity_log
              WHERE chat_id = ?
            ) sub
            WHERE grp = 0
            """,
            Integer.class, chatId);
        return streak != null ? streak : 0;
    }

    public Map<String, Object> getTodayActivity(long chatId) {
        var rows = jdbc.queryForList(
            "SELECT words_learned, quizzes_taken FROM activity_log WHERE chat_id = ? AND activity_date = CURRENT_DATE",
            chatId);
        if (rows.isEmpty()) return Map.of("wordsLearned", 0, "quizzesTaken", 0);
        Map<String, Object> row = rows.get(0);
        return Map.of(
            "wordsLearned", ((Number) row.get("words_learned")).intValue(),
            "quizzesTaken", ((Number) row.get("quizzes_taken")).intValue());
    }

    public Map<String, Object> getStats(long chatId) {
        var rows = jdbc.queryForList(
            """
            SELECT s.cefr_level, s.schedule, COUNT(sw.word_id) AS sent
            FROM subscribers s
            LEFT JOIN sent_words sw ON sw.chat_id = s.chat_id
            WHERE s.chat_id = ?
            GROUP BY s.cefr_level, s.schedule
            """,
            chatId);
        if (rows.isEmpty()) return Map.of("sent", 0, "level", "A1", "schedule", "0 9 * * *");
        Map<String, Object> row = rows.get(0);
        return Map.of(
            "sent", ((Number) row.get("sent")).intValue(),
            "level", row.get("cefr_level") != null ? row.get("cefr_level") : "A1",
            "schedule", row.get("schedule") != null ? row.get("schedule") : "0 9 * * *");
    }

    public static final int[] MILESTONES = {10, 25, 50, 100, 250, 500, 1000};

    public Integer checkMilestone(int totalWords) {
        for (int m : MILESTONES) {
            if (totalWords == m) return m;
        }
        return null;
    }
}
