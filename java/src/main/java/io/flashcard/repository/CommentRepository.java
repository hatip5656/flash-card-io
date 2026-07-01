package io.flashcard.repository;

import io.flashcard.model.WordComment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CommentRepository {

    private final JdbcTemplate jdbc;

    public CommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WordComment addComment(long chatId, String wordId, String comment) {
        var row = jdbc.queryForMap(
            """
            INSERT INTO word_comments (chat_id, word_id, comment) VALUES (?, ?, ?)
            RETURNING id, chat_id, comment, created_at
            """,
            chatId, wordId, comment);

        var firstNameRows = jdbc.queryForList(
            "SELECT first_name FROM subscribers WHERE chat_id = ?", String.class, chatId);
        String firstName = firstNameRows.isEmpty() ? null : firstNameRows.get(0);

        return new WordComment(
            ((Number) row.get("id")).intValue(),
            ((Number) row.get("chat_id")).longValue(),
            firstName,
            (String) row.get("comment"),
            ((java.sql.Timestamp) row.get("created_at")).toInstant()
        );
    }

    public List<WordComment> getComments(String wordId, int limit) {
        return jdbc.query(
            """
            SELECT wc.id, wc.chat_id, wc.comment, wc.created_at, s.first_name
            FROM word_comments wc
            LEFT JOIN subscribers s ON s.chat_id = wc.chat_id
            WHERE wc.word_id = ?
            ORDER BY wc.created_at DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new WordComment(
                rs.getInt("id"),
                rs.getLong("chat_id"),
                rs.getString("first_name"),
                rs.getString("comment"),
                rs.getTimestamp("created_at").toInstant()
            ),
            wordId, limit);
    }

    public int getCommentCount(String wordId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM word_comments WHERE word_id = ?", Integer.class, wordId);
        return count != null ? count : 0;
    }
}
