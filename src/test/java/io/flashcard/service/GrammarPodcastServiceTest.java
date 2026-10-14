package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrammarPodcastServiceTest {

    @TempDir Path cache;

    private JdbcTemplate jdbc;
    private NotificationService notifications;
    private GrammarPodcastService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        notifications = mock(NotificationService.class);
        service = new GrammarPodcastService(jdbc, mock(GeminiService.class), mock(AppProperties.class),
            mock(HttpClient.class), new ObjectMapper(), notifications, mock(SchedulerHistoryService.class),
            mock(AdminEventService.class), cache.toString());
    }

    private static Map<String, Object> lesson(String id) {
        return Map.of("id", id, "cefr_level", "A1", "topic", id, "podcast_script", "[]");
    }

    /** Each call returns the first pending lesson that is not in the skip list, like the real query. */
    private void pending(String... ids) {
        when(jdbc.queryForList(contains("podcast_id IS NULL"), any(Object.class))).thenAnswer(inv -> {
            List<String> skip = Arrays.asList((String[]) inv.getArgument(1));
            return Arrays.stream(ids).filter(id -> !skip.contains(id)).limit(1).map(GrammarPodcastServiceTest::lesson).toList();
        });
    }

    @Test
    void failedLessonIsSkippedSoTheRestStillRun() {
        // Empty scripts fail; the run must try each lesson once, then stop instead of retrying the first
        pending("a1-broken", "a1-next");

        service.generateAllPending();

        verify(notifications).error(anyString(), anyString(), contains("a1-broken"));
        verify(notifications).error(anyString(), anyString(), contains("a1-next"));
        verify(jdbc, times(3)).queryForList(contains("podcast_id IS NULL"), any(Object.class));
    }

    @Test
    void stopsWhenNothingIsPending() {
        pending();

        service.generateAllPending();

        verify(jdbc, times(1)).queryForList(contains("podcast_id IS NULL"), any(Object.class));
        verifyNoInteractions(notifications);
    }
}
