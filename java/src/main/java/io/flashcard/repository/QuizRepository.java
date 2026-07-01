package io.flashcard.repository;

import io.flashcard.model.QuizAnswer;
import io.flashcard.model.QuizResult;
import io.flashcard.model.QuizSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class QuizRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public QuizRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveQuizResult(long chatId, int score, int total, List<QuizAnswer> answers) {
        int percentage = Math.round((float) score / total * 100);
        Integer quizId = jdbc.queryForObject(
            "INSERT INTO quiz_results (chat_id, score, total, percentage) VALUES (?, ?, ?, ?) RETURNING id",
            Integer.class, chatId, score, total, percentage);

        if (quizId != null && !answers.isEmpty()) {
            for (QuizAnswer a : answers) {
                jdbc.update(
                    "INSERT INTO quiz_answers (quiz_id, estonian, correct_answer, user_answer, is_correct) VALUES (?, ?, ?, ?, ?)",
                    quizId, a.estonian(), a.correctAnswer(), a.userAnswer(), a.isCorrect());
            }
        }
    }

    public List<Map<String, Object>> getMostMissedWords(long chatId, int limit) {
        return jdbc.queryForList(
            """
            SELECT estonian, COUNT(*) as mistakes
            FROM quiz_answers qa
            JOIN quiz_results qr ON qa.quiz_id = qr.id
            WHERE qr.chat_id = ? AND qa.is_correct = false
            GROUP BY estonian
            ORDER BY mistakes DESC
            LIMIT ?
            """,
            chatId, limit);
    }

    public List<QuizResult> getQuizHistory(long chatId, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            """
            SELECT qr.id, qr.score, qr.total, qr.percentage, qr.completed_at,
                   qa.estonian, qa.correct_answer, qa.user_answer, qa.is_correct
            FROM (SELECT * FROM quiz_results WHERE chat_id = ? ORDER BY completed_at DESC LIMIT ?) qr
            LEFT JOIN quiz_answers qa ON qa.quiz_id = qr.id
            ORDER BY qr.completed_at DESC, qr.id DESC, qa.id ASC
            """,
            chatId, limit);

        Map<Integer, QuizResult> quizMap = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            int qid = ((Number) r.get("id")).intValue();
            quizMap.computeIfAbsent(qid, id -> {
                QuizResult qr = new QuizResult();
                qr.setId(qid);
                qr.setScore(((Number) r.get("score")).intValue());
                qr.setTotal(((Number) r.get("total")).intValue());
                qr.setPercentage(((Number) r.get("percentage")).intValue());
                qr.setCompletedAt(((java.sql.Timestamp) r.get("completed_at")).toInstant());
                qr.setAnswers(new ArrayList<>());
                return qr;
            });
            if (r.get("estonian") != null) {
                quizMap.get(qid).getAnswers().add(new QuizAnswer(
                    (String) r.get("estonian"),
                    (String) r.get("correct_answer"),
                    (String) r.get("user_answer"),
                    (Boolean) r.get("is_correct")
                ));
            }
        }
        return new ArrayList<>(quizMap.values());
    }

    public Map<String, Object> getQuizStats(long chatId) {
        return jdbc.queryForMap(
            """
            SELECT
              COUNT(*) AS total,
              COALESCE(AVG(percentage), 0) AS avg_pct,
              AVG(CASE WHEN rn <= 5 THEN percentage END) AS recent_avg,
              AVG(CASE WHEN rn > 5 AND rn <= 10 THEN percentage END) AS older_avg
            FROM (
              SELECT percentage, ROW_NUMBER() OVER (ORDER BY completed_at DESC) AS rn
              FROM quiz_results WHERE chat_id = ?
            ) sub
            """,
            chatId);
    }

    // --- Quiz Sessions ---

    public QuizSession getQuizSession(long chatId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT questions, answers, words, created_at FROM quiz_sessions WHERE chat_id = ? AND created_at > NOW() - INTERVAL '10 minutes'",
            chatId);
        if (rows.isEmpty()) return null;

        Map<String, Object> row = rows.get(0);
        try {
            QuizSession session = new QuizSession();
            session.setChatId(chatId);
            session.setQuestions(objectMapper.readValue((String) row.get("questions"),
                new TypeReference<List<QuizSession.QuizQuestion>>() {}));
            session.setAnswers(objectMapper.readValue((String) row.get("answers"),
                new TypeReference<List<QuizSession.AnswerEntry>>() {}));
            session.setWords(objectMapper.readValue((String) row.get("words"),
                new TypeReference<List<QuizSession.QuizWord>>() {}));
            session.setCreatedAt(((java.sql.Timestamp) row.get("created_at")).toInstant());
            return session;
        } catch (Exception e) {
            return null;
        }
    }

    public void upsertQuizSession(long chatId, List<QuizSession.QuizQuestion> questions, List<QuizSession.QuizWord> words) {
        try {
            String questionsJson = objectMapper.writeValueAsString(questions);
            String wordsJson = objectMapper.writeValueAsString(words);
            jdbc.update(
                """
                INSERT INTO quiz_sessions (chat_id, questions, answers, words, created_at)
                VALUES (?, ?::jsonb, '[]'::jsonb, ?::jsonb, NOW())
                ON CONFLICT (chat_id) DO UPDATE SET questions = ?::jsonb, answers = '[]'::jsonb, words = ?::jsonb, created_at = NOW()
                """,
                chatId, questionsJson, wordsJson, questionsJson, wordsJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize quiz session", e);
        }
    }

    public void pushQuizAnswer(long chatId, QuizSession.AnswerEntry answer) {
        try {
            String json = objectMapper.writeValueAsString(List.of(answer));
            jdbc.update(
                "UPDATE quiz_sessions SET answers = answers || ?::jsonb WHERE chat_id = ?",
                json, chatId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize quiz answer", e);
        }
    }

    public void deleteQuizSession(long chatId) {
        jdbc.update("DELETE FROM quiz_sessions WHERE chat_id = ?", chatId);
    }

    public int cleanExpiredQuizSessions() {
        return jdbc.update("DELETE FROM quiz_sessions WHERE created_at < NOW() - INTERVAL '10 minutes'");
    }
}
