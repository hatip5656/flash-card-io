package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class SentWordRepository {

    private final JdbcTemplate jdbc;

    public SentWordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void markWordSent(long chatId, String wordId, String wordValue, String english) {
        jdbc.update(
            "INSERT INTO sent_words (chat_id, word_id, word_value, english) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
            chatId, wordId, wordValue, english);
    }

    public List<String> getSentWordIds(long chatId) {
        return jdbc.queryForList("SELECT word_id FROM sent_words WHERE chat_id = ?", String.class, chatId);
    }

    public Set<String> getSentWordValues(long chatId) {
        return jdbc.queryForList(
            "SELECT word_value FROM sent_words WHERE chat_id = ? AND word_value IS NOT NULL",
            String.class, chatId)
            .stream().collect(Collectors.toSet());
    }

    public List<Map<String, Object>> getWordsDueForReview(long chatId, int limit) {
        return jdbc.queryForList(
            """
            SELECT word_id, word_value, english FROM sent_words
            WHERE chat_id = ? AND word_value IS NOT NULL AND english IS NOT NULL
              AND next_review <= CURRENT_DATE
            ORDER BY next_review ASC, ease_factor ASC
            LIMIT ?
            """,
            chatId, limit);
    }

    public void updateSm2(long chatId, String wordValue, int quality) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT ease_factor, interval_days, repetitions FROM sent_words WHERE chat_id = ? AND word_value = ?",
            chatId, wordValue);
        if (rows.isEmpty()) return;

        Map<String, Object> row = rows.get(0);
        double ef = ((Number) row.get("ease_factor")).doubleValue();
        int interval = ((Number) row.get("interval_days")).intValue();
        int reps = ((Number) row.get("repetitions")).intValue();

        if (quality >= 3) {
            if (reps == 0) interval = 1;
            else if (reps == 1) interval = 6;
            else interval = (int) Math.round(interval * ef);
            reps++;
        } else {
            reps = 0;
            interval = 1;
        }

        ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        if (ef < 1.3) ef = 1.3;

        jdbc.update(
            """
            UPDATE sent_words SET ease_factor = ?, interval_days = ?, repetitions = ?,
              next_review = CURRENT_DATE + ?::INTEGER
            WHERE chat_id = ? AND word_value = ?
            """,
            ef, interval, reps, interval, chatId, wordValue);
    }

    public List<Map<String, Object>> getSeenWordsByRecency(long chatId, int limit, int offset) {
        return jdbc.queryForList(
            """
            SELECT word_id, word_value, english FROM sent_words
            WHERE chat_id = ? AND word_value IS NOT NULL AND mastered = FALSE
            ORDER BY last_fed_at ASC NULLS FIRST, sent_at DESC
            LIMIT ? OFFSET ?
            """,
            chatId, limit, offset);
    }

    public List<Map<String, Object>> getLearnedWordsForQuiz(long chatId) {
        return jdbc.queryForList(
            """
            SELECT word_value, english, quiz_count FROM sent_words
            WHERE chat_id = ? AND word_value IS NOT NULL AND english IS NOT NULL AND mastered = FALSE
            ORDER BY last_quizzed_at ASC NULLS FIRST, quiz_count ASC
            """,
            chatId);
    }

    public void incrementQuizCount(long chatId, List<String> wordValues) {
        if (wordValues.isEmpty()) return;
        jdbc.update(
            "UPDATE sent_words SET quiz_count = quiz_count + 1, last_quizzed_at = NOW() WHERE chat_id = ? AND word_value = ANY(?)",
            chatId, wordValues.toArray(new String[0]));
    }

    public void trackFeedShown(long chatId, List<String> wordIds) {
        if (wordIds.isEmpty()) return;
        jdbc.update(
            "UPDATE sent_words SET feed_count = feed_count + 1, last_fed_at = NOW() WHERE chat_id = ? AND word_id = ANY(?)",
            chatId, wordIds.toArray(new String[0]));
    }

    public void trackCrushFound(long chatId, List<String> wordValues) {
        if (wordValues.isEmpty()) return;
        jdbc.update(
            "UPDATE sent_words SET crush_count = crush_count + 1, last_crushed_at = NOW() WHERE chat_id = ? AND word_value = ANY(?)",
            chatId, wordValues.toArray(new String[0]));
    }

    public void markWordMastered(long chatId, String wordId) {
        jdbc.update(
            "UPDATE sent_words SET mastered = TRUE, mastered_at = NOW() WHERE chat_id = ? AND word_id = ?",
            chatId, wordId);
    }

    public void unmarkWordMastered(long chatId, String wordId) {
        jdbc.update(
            "UPDATE sent_words SET mastered = FALSE, mastered_at = NULL WHERE chat_id = ? AND word_id = ?",
            chatId, wordId);
    }

    public int countSentWords(long chatId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sent_words WHERE chat_id = ?", Integer.class, chatId);
        return count != null ? count : 0;
    }

    public Map<String, Object> getWordCounts(long chatId) {
        return jdbc.queryForMap(
            """
            SELECT
              COUNT(*) as seen,
              COUNT(*) FILTER (WHERE mastered = TRUE) as mastered,
              COUNT(*) FILTER (WHERE quiz_count > 0) as quizzed,
              COALESCE(SUM(quiz_count), 0) as total_quiz_answers,
              COALESCE(SUM(feed_count), 0) as total_feed_views,
              COALESCE(SUM(crush_count), 0) as total_crush_finds
            FROM sent_words WHERE chat_id = ?
            """,
            chatId);
    }

    public int backfillEnglish(long chatId, String wordId, String english) {
        return jdbc.update(
            "UPDATE sent_words SET english = ? WHERE chat_id = ? AND word_id = ? AND english IS NULL",
            english, chatId, wordId);
    }

    public List<Map<String, Object>> getWordsNeedingEnglishBackfill() {
        return jdbc.queryForList(
            "SELECT chat_id, word_id, word_value FROM sent_words WHERE english IS NULL AND word_value IS NOT NULL");
    }
}
