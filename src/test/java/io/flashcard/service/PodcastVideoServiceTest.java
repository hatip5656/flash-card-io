package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PodcastVideoServiceTest {

    private static final String PODCAST = "40aa5265-2c7c-404b-a9b3-9f4e1a50a20a";
    private static final String URL = "https://wordagram.hatip.dev/videos/" + PODCAST + ".mp4";

    private JdbcTemplate jdbc;
    private TeacherSimulatorService simulator;
    private NotificationService notifications;
    private PodcastVideoService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        simulator = mock(TeacherSimulatorService.class);
        notifications = mock(NotificationService.class);
        service = new PodcastVideoService(jdbc, simulator, notifications, new ObjectMapper());
        when(jdbc.queryForList(contains("WHERE v.status IN"))).thenReturn(List.of(
            Map.of("podcast_id", PODCAST, "lesson_id", "a1-comitative-abessive", "topic", "With (-ga) and Without (-ta)")));
    }

    @Test
    void notifiesWithLinkWhenRenderCompletes() throws Exception {
        when(simulator.getStatus(PODCAST)).thenReturn(Optional.of(Map.of("id", PODCAST, "status", "done", "videoUrl", URL)));

        service.pollActiveRenders();

        verify(notifications).emit(eq("success"), eq("videos"), eq("Video ready: With (-ga) and Without (-ta)"),
            eq(URL), contains("\"videoUrl\":\"" + URL + "\""));
        verify(jdbc).update(contains("completed_at = NOW()"), eq("done"), eq("done"), isNull(), eq(URL), eq(PODCAST));
    }

    @Test
    void notifiesErrorWhenRenderFails() throws Exception {
        when(simulator.getStatus(PODCAST)).thenReturn(Optional.of(Map.of("id", PODCAST, "status", "failed", "error", "audio download failed: 404")));

        service.pollActiveRenders();

        verify(notifications).emit(eq("error"), eq("videos"), eq("Video failed: With (-ga) and Without (-ta)"),
            eq("audio download failed: 404"), anyString());
    }

    @Test
    void marksLostRenderAsFailedAfterServiceRestart() throws Exception {
        when(simulator.getStatus(PODCAST)).thenReturn(Optional.empty());

        service.pollActiveRenders();

        verify(notifications).emit(eq("error"), eq("videos"), anyString(), contains("restarted"), anyString());
    }

    @Test
    void onlyUpdatesProgressWhileRendering() throws Exception {
        when(simulator.getStatus(PODCAST)).thenReturn(Optional.of(Map.of("id", PODCAST, "status", "rendering", "progress", 0.42)));

        service.pollActiveRenders();

        verify(jdbc).update(contains("SET status = ?, progress = ?"), eq("rendering"), eq(0.42), eq(PODCAST));
        verifyNoInteractions(notifications);
    }

    @Test
    void keepsTrackingWhenServiceUnreachable() throws Exception {
        when(simulator.getStatus(PODCAST)).thenThrow(new IOException("connection refused"));

        service.pollActiveRenders();

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(notifications);
    }
}
