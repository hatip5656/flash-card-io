package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class StoryReadRepository {

    private final JdbcTemplate jdbc;

    public StoryReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void markStoryRead(long chatId, String storyId) {
        jdbc.update(
            "INSERT INTO story_reads (chat_id, story_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            chatId, storyId);
    }

    public Set<String> getReadStoryIds(long chatId) {
        return jdbc.queryForList(
            "SELECT story_id FROM story_reads WHERE chat_id = ?", String.class, chatId)
            .stream().collect(Collectors.toSet());
    }
}
