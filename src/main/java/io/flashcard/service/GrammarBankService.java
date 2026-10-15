package io.flashcard.service;

import io.flashcard.model.GrammarLesson;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GrammarBankService {

    private static final Logger log = LoggerFactory.getLogger(GrammarBankService.class);

    private static final String PODCAST_BASE_URL = "https://wordagram.hatip.dev/podcasts/";

    private final JdbcTemplate jdbc;
    private volatile List<GrammarLesson> lessons = List.of();

    public GrammarBankService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, cefr_level, topic, topic_tr, content, content_tr FROM grammar_lessons ORDER BY cefr_level, sort_order NULLS LAST, id");
            this.lessons = rows.stream().map(r -> new GrammarLesson(
                (String) r.get("id"),
                (String) r.get("cefr_level"),
                (String) r.get("topic"),
                (String) r.get("topic_tr"),
                (String) r.get("content"),
                (String) r.get("content_tr")
            )).toList();
            log.info("[grammar-bank] Loaded {} grammar lessons from database", lessons.size());
        } catch (Exception e) {
            log.warn("[grammar-bank] Failed to load from DB: {}", e.getMessage());
        }
    }

    public GrammarLesson getRandomLesson(String level, Set<String> sentIds) {
        List<GrammarLesson> available = lessons.stream()
            .filter(l -> l.cefrLevel().equals(level) && !sentIds.contains(l.id()))
            .toList();

        if (available.isEmpty()) {
            List<GrammarLesson> all = lessons.stream()
                .filter(l -> l.cefrLevel().equals(level))
                .toList();
            if (all.isEmpty()) return null;
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    public List<GrammarLesson> getAllLessons() {
        return lessons;
    }

    /** lessonId → {podcastId, podcastUrl, durationSeconds, hasVideo} for lessons with a ready podcast. */
    public Map<String, Map<String, Object>> getPodcastInfo() {
        Map<String, Map<String, Object>> podcastMap = new LinkedHashMap<>();
        try {
            var rows = jdbc.queryForList(
                "SELECT g.id AS lesson_id, g.podcast_id, p.audio_cache_key, p.duration_seconds, p.title AS podcast_title, " +
                "(v.podcast_id IS NOT NULL) AS has_video " +
                "FROM grammar_lessons g JOIN podcasts p ON p.id = g.podcast_id " +
                "LEFT JOIN podcast_videos v ON v.podcast_id = g.podcast_id AND v.status = 'done' AND v.video_url IS NOT NULL " +
                "WHERE p.status = 'ready'");
            for (var row : rows) {
                String lessonId = (String) row.get("lesson_id");
                String audioKey = (String) row.get("audio_cache_key");
                if (audioKey != null) {
                    var podcast = new HashMap<String, Object>();
                    podcast.put("podcastId", row.get("podcast_id") != null ? row.get("podcast_id").toString() : null);
                    podcast.put("podcastUrl", PODCAST_BASE_URL + audioKey);
                    podcast.put("durationSeconds", row.get("duration_seconds") != null ? row.get("duration_seconds") : 0);
                    podcast.put("hasVideo", Boolean.TRUE.equals(row.get("has_video")));
                    podcastMap.put(lessonId, podcast);
                }
            }
        } catch (Exception ignored) {}
        return podcastMap;
    }
}
