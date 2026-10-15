package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class LearningRepository {

    private final JdbcTemplate jdbc;

    public LearningRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Event(String itemType, String source, String itemId, String wordId, String estonian,
                        String grammarLessonId, String skill, String questionType, Boolean isCorrect,
                        String userAnswer, String correctAnswer, Integer latencyMs, String metaJson) {}

    public void insertEvents(long chatId, List<Event> events) {
        if (events.isEmpty()) return;
        jdbc.batchUpdate("""
            INSERT INTO learning_events (chat_id, item_type, source, item_id, word_id, estonian, grammar_lesson_id,
              skill, question_type, is_correct, user_answer, correct_answer, latency_ms, meta)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
            """,
            events.stream().map(e -> new Object[]{chatId, e.itemType(), e.source(), e.itemId(), e.wordId(),
                e.estonian(), e.grammarLessonId(), e.skill(), e.questionType(), e.isCorrect(), e.userAnswer(),
                e.correctAnswer(), e.latencyMs(), e.metaJson()}).toList());
    }

    // ── Grammar mastery ──

    public Map<String, Object> getMastery(long chatId, String lessonId) {
        var rows = jdbc.queryForList(
            "SELECT * FROM grammar_mastery WHERE chat_id = ? AND grammar_lesson_id = ?", chatId, lessonId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveMastery(long chatId, String lessonId, int attempts, int correct, int examAttempts,
                            int examMisses, double mastery, int recheckDays) {
        jdbc.update("""
            INSERT INTO grammar_mastery (chat_id, grammar_lesson_id, attempts, correct, exam_attempts, exam_misses,
              mastery, last_practiced_at, next_check_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW() + make_interval(days => ?))
            ON CONFLICT (chat_id, grammar_lesson_id) DO UPDATE SET attempts = EXCLUDED.attempts,
              correct = EXCLUDED.correct, exam_attempts = EXCLUDED.exam_attempts, exam_misses = EXCLUDED.exam_misses,
              mastery = EXCLUDED.mastery, last_practiced_at = NOW(), next_check_at = EXCLUDED.next_check_at
            """,
            chatId, lessonId, attempts, correct, examAttempts, examMisses, mastery, recheckDays);
    }

    /** Every mastery row for the learner, with a flag for whether the check-in is due. */
    public List<Map<String, Object>> getAllMastery(long chatId) {
        return jdbc.queryForList("""
            SELECT grammar_lesson_id, attempts, correct, exam_attempts, exam_misses, mastery,
                   last_practiced_at, (next_check_at <= NOW()) AS due
            FROM grammar_mastery WHERE chat_id = ?
            """, chatId);
    }

    // ── Exam evidence ──

    /**
     * Latest answer per exam question at the given exam ids (feed and full exams alike). skill is the
     * question's tagged sub-skill when it has one; exam_skill is the exam's own (reading, listening, …).
     */
    public List<Map<String, Object>> getLatestExamAnswers(long chatId, Set<String> examIds) {
        if (examIds.isEmpty()) return List.of();
        return jdbc.queryForList("""
            SELECT a.item_id, a.skill, a.is_correct, a.created_at, e.skill AS exam_skill FROM (
              SELECT DISTINCT ON (item_id) item_id, skill, is_correct, created_at
              FROM learning_events
              WHERE chat_id = ? AND item_type = 'exam_question' AND is_correct IS NOT NULL
                AND split_part(item_id, '#', 1) = ANY(?)
                AND created_at > NOW() - INTERVAL '90 days'
              ORDER BY item_id, created_at DESC
            ) a LEFT JOIN official_exams e ON e.id = split_part(a.item_id, '#', 1)
            """, chatId, examIds.toArray(new String[0]));
    }

    // ── Goal ──

    /** {target_level, source} or null when the learner has not set a goal or taken the placement test. */
    public Map<String, Object> getGoal(long chatId) {
        var rows = jdbc.queryForList("SELECT target_level, source FROM learner_goals WHERE chat_id = ?", chatId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void saveGoal(long chatId, String targetLevel, String source) {
        jdbc.update("""
            INSERT INTO learner_goals (chat_id, target_level, source) VALUES (?, ?, ?)
            ON CONFLICT (chat_id) DO UPDATE SET target_level = EXCLUDED.target_level, source = EXCLUDED.source,
              updated_at = NOW()
            """, chatId, targetLevel, source);
    }

    /** Writing/speaking tasks at a level: how many exist and how many the learner has answered, per skill. */
    public List<Map<String, Object>> getTaskProgress(long chatId, String level) {
        return jdbc.queryForList("""
            SELECT t.skill, SUM(jsonb_array_length(t.tasks)) AS tasks,
                   (SELECT COUNT(DISTINCT (r.task_set_id, r.task_index)) FROM exam_task_responses r
                    JOIN exam_tasks t2 ON t2.id = r.task_set_id
                    WHERE r.chat_id = ? AND t2.cefr_level = ? AND t2.skill = t.skill) AS answered
            FROM exam_tasks t WHERE t.cefr_level = ? GROUP BY t.skill ORDER BY t.skill
            """, chatId, level, level);
    }

    /** Exam questions the learner answered in the feed recently (not to be shown again yet). */
    public Set<String> getRecentExamItemIds(long chatId, int days) {
        return Set.copyOf(jdbc.queryForList("""
            SELECT DISTINCT item_id FROM learning_events
            WHERE chat_id = ? AND item_type = 'exam_question' AND created_at > NOW() - make_interval(days => ?)
            """, String.class, chatId, days));
    }

    /** Exam questions whose latest answer was wrong, oldest miss first. */
    public List<String> getMissedExamItemIds(long chatId, int olderThanDays) {
        return jdbc.queryForList("""
            SELECT item_id FROM (
              SELECT DISTINCT ON (item_id) item_id, is_correct, created_at FROM learning_events
              WHERE chat_id = ? AND item_type = 'exam_question' AND is_correct IS NOT NULL
              ORDER BY item_id, created_at DESC
            ) latest
            WHERE is_correct = FALSE AND created_at < NOW() - make_interval(days => ?)
            ORDER BY created_at
            """, String.class, chatId, olderThanDays);
    }

    public void saveExamAttempt(long chatId, String examId, int score, int total, String answersJson,
                                String sectionsJson, Integer durationSeconds) {
        jdbc.update("""
            INSERT INTO exam_attempts (chat_id, exam_id, score, total, answers, sections, duration_seconds, server_graded)
            VALUES (?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, TRUE)
            """, chatId, examId, score, total, answersJson, sectionsJson, durationSeconds);
    }

    public Map<String, Object> getLastAttempt(long chatId, String examId) {
        var rows = jdbc.queryForList("""
            SELECT exam_id, score, total, completed_at FROM exam_attempts
            WHERE chat_id = ? AND exam_id = ? ORDER BY completed_at DESC LIMIT 1
            """, chatId, examId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
