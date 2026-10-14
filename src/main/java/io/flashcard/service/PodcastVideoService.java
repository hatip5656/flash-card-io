package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks classroom-video renders for grammar podcasts. Renders run on the teacher-simulator
 * service; this polls it and raises an admin notification when a video is ready or fails.
 */
@Service
public class PodcastVideoService {

    private static final Logger log = LoggerFactory.getLogger(PodcastVideoService.class);
    private static final String CATEGORY = "videos";

    private final JdbcTemplate jdbc;
    private final TeacherSimulatorService simulator;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    public PodcastVideoService(JdbcTemplate jdbc, TeacherSimulatorService simulator,
                               NotificationService notifications, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.simulator = simulator;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    /** Queue a render on the video service and start tracking it. */
    public Map<String, Object> request(String lessonId, String podcastId, String audioUrl,
                                       List<Map<String, Object>> subtitles, boolean force)
            throws IOException, InterruptedException {
        Map<String, Object> status = simulator.requestVideo(podcastId, audioUrl, subtitles, force);
        String state = String.valueOf(status.get("status"));
        jdbc.update("""
            INSERT INTO podcast_videos (podcast_id, lesson_id, status, progress, error, video_url, requested_at, completed_at)
            VALUES (?, ?, ?, ?, NULL, ?, NOW(), CASE WHEN ? = 'done' THEN NOW() END)
            ON CONFLICT (podcast_id) DO UPDATE SET
                lesson_id = EXCLUDED.lesson_id, status = EXCLUDED.status, progress = EXCLUDED.progress,
                error = NULL, video_url = EXCLUDED.video_url,
                requested_at = NOW(), completed_at = EXCLUDED.completed_at
            """, podcastId, lessonId, state, progressOf(status), status.get("videoUrl"), state);
        return statusFor(podcastId);
    }

    /** The tracked state of a podcast's video: status, progress, error and public videoUrl. */
    public Map<String, Object> statusFor(String podcastId) {
        var rows = jdbc.queryForList(
            "SELECT status, progress, error, video_url FROM podcast_videos WHERE podcast_id = ?", podcastId);
        var result = new LinkedHashMap<String, Object>();
        result.put("id", podcastId);
        if (rows.isEmpty()) {
            result.put("status", "none");
            return result;
        }
        var row = rows.get(0);
        result.put("status", row.get("status"));
        result.put("progress", row.get("progress"));
        if (row.get("error") != null) result.put("error", row.get("error"));
        if (row.get("video_url") != null) result.put("videoUrl", row.get("video_url"));
        return result;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void pollActiveRenders() {
        var active = jdbc.queryForList("""
            SELECT v.podcast_id, v.lesson_id, g.topic
            FROM podcast_videos v
            LEFT JOIN grammar_lessons g ON g.id = v.lesson_id
            WHERE v.status IN ('queued', 'rendering')
            """);
        for (var row : active) {
            String podcastId = (String) row.get("podcast_id");
            String lessonId = (String) row.get("lesson_id");
            String topic = row.get("topic") != null ? (String) row.get("topic") : lessonId;
            try {
                Optional<Map<String, Object>> status = simulator.getStatus(podcastId);
                if (status.isEmpty()) {
                    finish(podcastId, lessonId, topic, "failed", null,
                        "The render was lost because the video service restarted. Generate it again.");
                    continue;
                }
                var s = status.get();
                switch (String.valueOf(s.get("status"))) {
                    case "done" -> finish(podcastId, lessonId, topic, "done", (String) s.get("videoUrl"), null);
                    case "failed" -> finish(podcastId, lessonId, topic, "failed", null,
                        s.get("error") != null ? s.get("error").toString() : "unknown error");
                    default -> jdbc.update("UPDATE podcast_videos SET status = ?, progress = ? WHERE podcast_id = ?",
                        s.get("status"), progressOf(s), podcastId);
                }
            } catch (Exception e) {
                // Service unreachable: keep tracking, try again next poll
                log.debug("Video status check for {} failed: {}", podcastId, e.getMessage());
            }
        }
    }

    private void finish(String podcastId, String lessonId, String topic, String state, String videoUrl, String error) {
        jdbc.update("""
            UPDATE podcast_videos SET status = ?, progress = CASE WHEN ? = 'done' THEN 1 ELSE progress END,
                error = ?, video_url = ?, completed_at = NOW()
            WHERE podcast_id = ?
            """, state, state, error, videoUrl, podcastId);

        String metadata = metadataJson(lessonId, podcastId, videoUrl);
        if ("done".equals(state)) {
            notifications.emit("success", CATEGORY, "Video ready: " + topic, videoUrl, metadata);
            log.info("Video ready for lesson {}: {}", lessonId, videoUrl);
        } else {
            notifications.emit("error", CATEGORY, "Video failed: " + topic, error, metadata);
            log.warn("Video failed for lesson {}: {}", lessonId, error);
        }
    }

    private String metadataJson(String lessonId, String podcastId, String videoUrl) {
        var m = new LinkedHashMap<String, Object>();
        m.put("lessonId", lessonId);
        m.put("podcastId", podcastId);
        if (videoUrl != null) m.put("videoUrl", videoUrl);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }

    private static double progressOf(Map<String, Object> status) {
        return status.get("progress") instanceof Number n ? n.doubleValue() : 0;
    }
}
