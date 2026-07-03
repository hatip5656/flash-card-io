package io.flashcard.repository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class AdventureRepository {

    private final JdbcTemplate jdbc;

    public AdventureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable(value = "adventure_stories")
    public List<Map<String, Object>> listStories() {
        return jdbc.queryForList("""
            SELECT s.*,
              (SELECT COUNT(*) FROM story_nodes n WHERE n.story_id = s.id) AS node_count
            FROM adventure_stories s
            ORDER BY s.sort_order
            """);
    }

    @Cacheable(value = "story_progress", key = "#chatId")
    public Map<String, Object> getUserProgress(long chatId) {
        var rows = jdbc.queryForList(
            "SELECT story_id, current_node_id, words_learned, completed FROM story_progress WHERE chat_id = ?",
            chatId);
        Map<String, Object> progressMap = new HashMap<>();
        for (var r : rows) {
            progressMap.put((String) r.get("story_id"), Map.of(
                "currentNodeId", r.get("current_node_id"),
                "wordsLearned", r.get("words_learned"),
                "completed", r.get("completed")));
        }
        return progressMap;
    }

    @Cacheable(value = "adventure_story_detail", key = "#storyId")
    public Map<String, Object> getStory(String storyId) {
        var storyRows = jdbc.queryForList("SELECT * FROM adventure_stories WHERE id = ?", storyId);
        if (storyRows.isEmpty()) return null;
        return storyRows.get(0);
    }

    public List<Map<String, Object>> getNodes(String storyId) {
        return jdbc.queryForList(
            "SELECT * FROM story_nodes WHERE story_id = ? ORDER BY sort_order", storyId);
    }

    public List<Map<String, Object>> getVocabulary(String storyId) {
        return jdbc.queryForList(
            "SELECT * FROM story_node_vocabulary WHERE story_id = ? ORDER BY sort_order", storyId);
    }

    public List<Map<String, Object>> getChoices(String storyId) {
        return jdbc.queryForList(
            "SELECT * FROM story_node_choices WHERE story_id = ? ORDER BY sort_order", storyId);
    }

    public List<Map<String, Object>> getMinigames(String storyId) {
        return jdbc.queryForList(
            "SELECT * FROM story_node_minigame WHERE story_id = ?", storyId);
    }

    @Caching(evict = {
        @CacheEvict(value = "story_progress", key = "#chatId"),
        @CacheEvict(value = "story_progress", key = "#chatId + '_' + #storyId")
    })
    public void saveProgress(long chatId, String storyId, String currentNodeId, int wordsLearned, boolean completed) {
        jdbc.update("""
            INSERT INTO story_progress (chat_id, story_id, current_node_id, words_learned, completed, completed_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (chat_id, story_id) DO UPDATE SET
              current_node_id = ?,
              words_learned = ?,
              completed = ?,
              completed_at = ?,
              updated_at = NOW()
            """,
            chatId, storyId, currentNodeId, wordsLearned, completed,
            completed ? new java.sql.Timestamp(System.currentTimeMillis()) : null,
            currentNodeId, wordsLearned, completed,
            completed ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
    }

    @Cacheable(value = "story_progress", key = "#chatId + '_' + #storyId")
    public Map<String, Object> getProgress(long chatId, String storyId) {
        var rows = jdbc.queryForList(
            "SELECT * FROM story_progress WHERE chat_id = ? AND story_id = ?", chatId, storyId);
        if (rows.isEmpty()) return null;
        var p = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentNodeId", p.get("current_node_id"));
        result.put("wordsLearned", p.get("words_learned"));
        result.put("completed", p.get("completed"));
        result.put("startedAt", p.get("started_at"));
        result.put("updatedAt", p.get("updated_at"));
        return result;
    }
}
