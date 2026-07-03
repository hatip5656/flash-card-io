package io.flashcard.repository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SavedWordRepository {

    private final JdbcTemplate jdbc;

    public SavedWordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Caching(evict = {
        @CacheEvict(value = "saved_word_ids", key = "#chatId"),
        @CacheEvict(value = "saved_word_count", key = "#chatId")
    })
    public void saveWord(long chatId, String wordId) {
        jdbc.update(
            "INSERT INTO saved_words (chat_id, word_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            chatId, wordId);
    }

    @Caching(evict = {
        @CacheEvict(value = "saved_word_ids", key = "#chatId"),
        @CacheEvict(value = "saved_word_count", key = "#chatId")
    })
    public void unsaveWord(long chatId, String wordId) {
        jdbc.update("DELETE FROM saved_words WHERE chat_id = ? AND word_id = ?", chatId, wordId);
    }

    @Cacheable(value = "saved_word_ids", key = "#chatId")
    public List<String> getSavedWordIds(long chatId) {
        return jdbc.queryForList(
            "SELECT word_id FROM saved_words WHERE chat_id = ? ORDER BY saved_at DESC",
            String.class, chatId);
    }

    @Cacheable(value = "saved_word_count", key = "#chatId")
    public int countSavedWords(long chatId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM saved_words WHERE chat_id = ?", Integer.class, chatId);
        return count != null ? count : 0;
    }
}
