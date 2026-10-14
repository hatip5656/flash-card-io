package io.flashcard.repository;

import io.flashcard.model.Podcast;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PodcastRepository {

    private final JdbcClient jdbc;

    public PodcastRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String create(Podcast podcast) {
        String id = UUID.randomUUID().toString();
        jdbc.sql("""
            INSERT INTO podcasts (id, chat_id, title, description, cefr_level, status)
            VALUES (:id, :chatId, :title, :description, :cefrLevel, :status)
            """)
            .param("id", id)
            .param("chatId", podcast.getChatId())
            .param("title", podcast.getTitle())
            .param("description", podcast.getDescription())
            .param("cefrLevel", podcast.getCefrLevel())
            .param("status", "pending")
            .update();
        return id;
    }

    public void updateStatus(String id, String status, String errorMessage) {
        jdbc.sql("UPDATE podcasts SET status = :status, error_message = :error WHERE id = :id")
            .param("id", id)
            .param("status", status)
            .param("error", errorMessage)
            .update();
    }

    public void updateReady(String id, String script, String audioCacheKey, int durationSeconds, String title) {
        jdbc.sql("""
            UPDATE podcasts
            SET status = 'ready', script = :script::jsonb, audio_cache_key = :cacheKey,
                duration_seconds = :duration, title = :title
            WHERE id = :id
            """)
            .param("id", id)
            .param("script", script)
            .param("cacheKey", audioCacheKey)
            .param("duration", durationSeconds)
            .param("title", title)
            .update();
    }

    public Optional<Podcast> findById(String id) {
        return jdbc.sql("SELECT * FROM podcasts WHERE id = :id")
            .param("id", id)
            .query(this::mapRow)
            .optional();
    }

    public Optional<Podcast> findLatestReady(long chatId) {
        return jdbc.sql("""
            SELECT * FROM podcasts
            WHERE chat_id = :chatId AND status = 'ready'
            ORDER BY created_at DESC LIMIT 1
            """)
            .param("chatId", chatId)
            .query(this::mapRow)
            .optional();
    }

    public List<Podcast> findHistory(long chatId, int limit) {
        return jdbc.sql("""
            SELECT * FROM podcasts
            WHERE chat_id = :chatId AND status = 'ready'
            ORDER BY created_at DESC LIMIT :limit
            """)
            .param("chatId", chatId)
            .param("limit", limit)
            .query(this::mapRow)
            .list();
    }

    public boolean hasGeneratingPodcast(long chatId) {
        return jdbc.sql("""
            SELECT COUNT(*) FROM podcasts
            WHERE chat_id = :chatId AND status IN ('pending', 'generating')
            """)
            .param("chatId", chatId)
            .query(Integer.class)
            .single() > 0;
    }

    public List<Podcast> findAll(int limit, int offset) {
        return jdbc.sql("SELECT * FROM podcasts ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
            .param("limit", limit)
            .param("offset", offset)
            .query(this::mapRow)
            .list();
    }

    public int countAll() {
        return jdbc.sql("SELECT COUNT(*) FROM podcasts")
            .query(Integer.class)
            .single();
    }

    public List<Podcast> findByChatId(long chatId, int limit, int offset) {
        return jdbc.sql("SELECT * FROM podcasts WHERE chat_id = :chatId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
            .param("chatId", chatId)
            .param("limit", limit)
            .param("offset", offset)
            .query(this::mapRow)
            .list();
    }

    public int countByChatId(long chatId) {
        return jdbc.sql("SELECT COUNT(*) FROM podcasts WHERE chat_id = :chatId")
            .param("chatId", chatId)
            .query(Integer.class)
            .single();
    }

    public void deleteById(String id) {
        jdbc.sql("DELETE FROM podcasts WHERE id = :id").param("id", id).update();
    }

    /**
     * Podcasts admins can assign: admin-made ones and the current grammar lesson podcasts.
     * Superseded grammar podcasts (no longer linked to a lesson) are left out.
     */
    public List<Map<String, Object>> findAssignable() {
        return jdbc.sql("""
            SELECT p.id, p.title, p.cefr_level, p.duration_seconds, p.audio_cache_key, p.created_at,
                   g.id AS lesson_id
            FROM podcasts p
            LEFT JOIN grammar_lessons g ON g.podcast_id = p.id
            WHERE p.chat_id = 0 AND p.status = 'ready' AND p.audio_cache_key IS NOT NULL
              AND (p.title NOT LIKE 'Grammar:%' OR g.id IS NOT NULL)
            ORDER BY p.cefr_level, p.title
            """)
            .query()
            .listOfRows();
    }

    /** All of a user's podcasts with what is needed to match them to assignable ones. */
    public List<Map<String, Object>> findForUser(long chatId) {
        return jdbc.sql("""
            SELECT id, title, cefr_level, duration_seconds, audio_cache_key, status, created_at
            FROM podcasts WHERE chat_id = :chatId ORDER BY created_at DESC
            """)
            .param("chatId", chatId)
            .query()
            .listOfRows();
    }

    public boolean userHasAudio(long chatId, String audioCacheKey) {
        return jdbc.sql("SELECT COUNT(*) FROM podcasts WHERE chat_id = :chatId AND audio_cache_key = :key")
            .param("chatId", chatId)
            .param("key", audioCacheKey)
            .query(Integer.class)
            .single() > 0;
    }

    public String cloneForUser(String sourcePodcastId, long chatId) {
        String newId = UUID.randomUUID().toString();
        jdbc.sql("""
            INSERT INTO podcasts (id, chat_id, title, description, script, audio_cache_key,
                duration_seconds, cefr_level, status, created_at)
            SELECT :newId, :chatId, title, description, script, audio_cache_key,
                duration_seconds, cefr_level, status, NOW()
            FROM podcasts WHERE id = :sourceId AND status = 'ready'
            """)
            .param("newId", newId)
            .param("chatId", chatId)
            .param("sourceId", sourcePodcastId)
            .update();
        return newId;
    }

    private Podcast mapRow(ResultSet rs, int rowNum) throws SQLException {
        Podcast p = new Podcast();
        p.setId(rs.getString("id"));
        p.setChatId(rs.getLong("chat_id"));
        p.setTitle(rs.getString("title"));
        p.setDescription(rs.getString("description"));
        p.setScript(rs.getString("script"));
        p.setAudioCacheKey(rs.getString("audio_cache_key"));
        p.setDurationSeconds(rs.getInt("duration_seconds"));
        p.setCefrLevel(rs.getString("cefr_level"));
        p.setStatus(rs.getString("status"));
        p.setErrorMessage(rs.getString("error_message"));
        var ts = rs.getTimestamp("created_at");
        if (ts != null) p.setCreatedAt(ts.toLocalDateTime());
        return p;
    }
}
