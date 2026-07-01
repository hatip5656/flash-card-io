package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class GrammarRepository {

    private final JdbcTemplate jdbc;

    public GrammarRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> getSentGrammarIds(long chatId) {
        return jdbc.queryForList(
            "SELECT lesson_id FROM sent_grammar WHERE chat_id = ?", String.class, chatId)
            .stream().collect(Collectors.toSet());
    }

    public void markGrammarSent(long chatId, String lessonId) {
        jdbc.update(
            "INSERT INTO sent_grammar (chat_id, lesson_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            chatId, lessonId);
    }
}
