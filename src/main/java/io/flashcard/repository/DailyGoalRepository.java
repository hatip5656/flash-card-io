package io.flashcard.repository;

import io.flashcard.model.DailyGoalRow;
import io.flashcard.model.DailyGoalSettings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Daily goal settings, per-day goal rows and push devices. Dates are the learner's (Tallinn) day. */
@Repository
public class DailyGoalRepository {

    /** Columns that mark a one-time event on a goal row; only these may be claimed. */
    private static final Set<String> CLAIMABLE = Set.of("completed_at", "morning_sent_at", "evening_sent_at", "done_sent_at");
    private static final Set<String> TIME_COLUMNS = Set.of("morning_time", "evening_time");

    static final int SEND_WINDOW_HOURS = 3;

    private final JdbcTemplate jdbc;

    public DailyGoalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Settings ──────────────────────────────────

    public Optional<DailyGoalSettings> findSettings(long chatId) {
        return jdbc.query("SELECT * FROM daily_goal_settings WHERE chat_id = ?", (rs, i) -> new DailyGoalSettings(
            rs.getBoolean("words_enabled"), rs.getInt("words_target"),
            rs.getBoolean("quiz_enabled"), rs.getInt("quiz_target"),
            rs.getBoolean("reviews_enabled"), rs.getBoolean("grammar_enabled"),
            rs.getBoolean("notify_enabled"),
            rs.getTime("morning_time").toLocalTime(), rs.getTime("evening_time").toLocalTime()), chatId)
            .stream().findFirst();
    }

    public void saveSettings(long chatId, DailyGoalSettings s) {
        jdbc.update("""
            INSERT INTO daily_goal_settings (chat_id, words_enabled, words_target, quiz_enabled, quiz_target,
                reviews_enabled, grammar_enabled, notify_enabled, morning_time, evening_time, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (chat_id) DO UPDATE SET
                words_enabled = EXCLUDED.words_enabled, words_target = EXCLUDED.words_target,
                quiz_enabled = EXCLUDED.quiz_enabled, quiz_target = EXCLUDED.quiz_target,
                reviews_enabled = EXCLUDED.reviews_enabled, grammar_enabled = EXCLUDED.grammar_enabled,
                notify_enabled = EXCLUDED.notify_enabled, morning_time = EXCLUDED.morning_time,
                evening_time = EXCLUDED.evening_time, updated_at = NOW()
            """, chatId, s.wordsEnabled(), s.wordsTarget(), s.quizEnabled(), s.quizTarget(),
            s.reviewsEnabled(), s.grammarEnabled(), s.notifyEnabled(), s.morningTime(), s.eveningTime());
    }

    // ── Goal rows ─────────────────────────────────

    private static final RowMapper<DailyGoalRow> ROW = (rs, i) -> new DailyGoalRow(
        rs.getLong("chat_id"), rs.getObject("goal_date", LocalDate.class),
        rs.getInt("words_target"), rs.getInt("quiz_target"), rs.getInt("reviews_target"),
        rs.getString("grammar_lesson_id"), rs.getInt("quiz_done"), rs.getInt("reviews_done"),
        instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("morning_sent_at")),
        instant(rs.getTimestamp("evening_sent_at")), instant(rs.getTimestamp("done_sent_at")));

    public Optional<DailyGoalRow> find(long chatId, LocalDate date) {
        return jdbc.query("SELECT * FROM daily_goals WHERE chat_id = ? AND goal_date = ?", ROW, chatId, date)
            .stream().findFirst();
    }

    /** Store the row unless one already exists for that day; returns the stored row either way. */
    public DailyGoalRow insertIfAbsent(DailyGoalRow row) {
        jdbc.update("""
            INSERT INTO daily_goals (chat_id, goal_date, words_target, quiz_target, reviews_target, grammar_lesson_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (chat_id, goal_date) DO NOTHING
            """, row.chatId(), row.date(), row.wordsTarget(), row.quizTarget(), row.reviewsTarget(), row.grammarLessonId());
        return find(row.chatId(), row.date()).orElseThrow();
    }

    public void addQuiz(long chatId, LocalDate date, int answers, int dueAnswers) {
        jdbc.update("""
            UPDATE daily_goals SET quiz_done = quiz_done + ?, reviews_done = reviews_done + ?
            WHERE chat_id = ? AND goal_date = ?
            """, answers, dueAnswers, chatId, date);
    }

    /** Set a one-time marker; true only for the caller that set it. */
    public boolean claim(long chatId, LocalDate date, String column) {
        if (!CLAIMABLE.contains(column)) throw new IllegalArgumentException("Not a claimable column: " + column);
        return jdbc.update("UPDATE daily_goals SET " + column + " = NOW() WHERE chat_id = ? AND goal_date = ? AND "
            + column + " IS NULL", chatId, date) == 1;
    }

    // ── Progress sources ──────────────────────────

    public int wordsSeenSince(long chatId, Instant dayStart) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sent_words WHERE chat_id = ? AND sent_at >= ?",
            Integer.class, chatId, Timestamp.from(dayStart));
        return n != null ? n : 0;
    }

    public int reviewsDueOn(long chatId, LocalDate date) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sent_words WHERE chat_id = ? AND next_review <= ?",
            Integer.class, chatId, date);
        return n != null ? n : 0;
    }

    /** How many of these words were due for review on that date. Call before their review is recorded. */
    public int countDue(long chatId, LocalDate date, List<String> estonian) {
        if (estonian.isEmpty()) return 0;
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT word_value) FROM sent_words
            WHERE chat_id = ? AND next_review <= ? AND word_value = ANY (?)
            """, Integer.class, chatId, date, estonian.toArray(String[]::new));
        return n != null ? n : 0;
    }

    public boolean grammarPracticedSince(long chatId, Instant dayStart, String level) {
        Boolean any = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM grammar_mastery m JOIN grammar_lessons l ON l.id = m.grammar_lesson_id
                           WHERE m.chat_id = ? AND m.last_practiced_at >= ? AND l.cefr_level = ?)
            """, Boolean.class, chatId, Timestamp.from(dayStart), level);
        return Boolean.TRUE.equals(any);
    }

    // ── Notifications ─────────────────────────────

    /**
     * Learners with notifications on and a push device, whose time (morning or evening) came within the
     * last {@value #SEND_WINDOW_HOURS} hours and who have not had that message today. The window keeps a
     * learner who turns notifications on late in the day from getting the morning message at night.
     * A learner without settings uses the defaults.
     */
    public List<Long> learnersDueFor(String sentColumn, String timeColumn, LocalDate date, LocalTime now) {
        if (!CLAIMABLE.contains(sentColumn) || !TIME_COLUMNS.contains(timeColumn)) {
            throw new IllegalArgumentException("Bad column");
        }
        String defaultTime = "morning_time".equals(timeColumn) ? "09:00" : "20:00";
        return jdbc.queryForList("""
            SELECT DISTINCT d.chat_id FROM push_devices d
            LEFT JOIN daily_goal_settings s ON s.chat_id = d.chat_id
            LEFT JOIN daily_goals g ON g.chat_id = d.chat_id AND g.goal_date = ?
            WHERE COALESCE(s.notify_enabled, TRUE)
              AND (?::time - COALESCE(s.%s, TIME '%s')) BETWEEN INTERVAL '0' AND make_interval(hours => %d)
              AND (g.chat_id IS NULL OR g.%s IS NULL)
            """.formatted(timeColumn, defaultTime, SEND_WINDOW_HOURS, sentColumn), Long.class, date, now);
    }

    /** Consecutive completed days ending today, or yesterday while today is not complete yet. */
    public int completedStreak(long chatId, LocalDate today) {
        List<LocalDate> days = jdbc.queryForList("""
            SELECT goal_date FROM daily_goals WHERE chat_id = ? AND completed_at IS NOT NULL AND goal_date <= ?
            ORDER BY goal_date DESC LIMIT 400
            """, LocalDate.class, chatId, today);
        LocalDate expect = !days.isEmpty() && days.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate d : days) {
            if (!d.equals(expect)) break;
            streak++;
            expect = expect.minusDays(1);
        }
        return streak;
    }

    // ── Devices ───────────────────────────────────

    public void upsertDevice(String token, long chatId, String platform) {
        jdbc.update("""
            INSERT INTO push_devices (token, chat_id, platform) VALUES (?, ?, ?)
            ON CONFLICT (token) DO UPDATE SET chat_id = EXCLUDED.chat_id, platform = EXCLUDED.platform, last_seen_at = NOW()
            """, token, chatId, platform);
    }

    public void deleteDevice(String token) {
        jdbc.update("DELETE FROM push_devices WHERE token = ?", token);
    }

    public List<String> deviceTokens(long chatId) {
        return jdbc.queryForList("SELECT token FROM push_devices WHERE chat_id = ?", String.class, chatId);
    }

    /** Remove a deleted learner's goal data and devices. */
    public void forget(long chatId) {
        jdbc.update("DELETE FROM daily_goals WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM daily_goal_settings WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM push_devices WHERE chat_id = ?", chatId);
    }

    private static Instant instant(Timestamp t) {
        return t != null ? t.toInstant() : null;
    }
}
