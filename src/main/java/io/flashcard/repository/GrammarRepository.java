package io.flashcard.repository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(value = "grammar_sent_ids", key = "#chatId")
    public Set<String> getSentGrammarIds(long chatId) {
        return jdbc.queryForList(
            "SELECT lesson_id FROM sent_grammar WHERE chat_id = ?", String.class, chatId)
            .stream().collect(Collectors.toSet());
    }

    @CacheEvict(value = "grammar_sent_ids", key = "#chatId")
    public void markGrammarSent(long chatId, String lessonId) {
        jdbc.update(
            // sent_at is the last read, so re-reading a lesson counts toward today's goal
            "INSERT INTO sent_grammar (chat_id, lesson_id) VALUES (?, ?) ON CONFLICT (chat_id, lesson_id) DO UPDATE SET sent_at = NOW()",
            chatId, lessonId);
    }
}
