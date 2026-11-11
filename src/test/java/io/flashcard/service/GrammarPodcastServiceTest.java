package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrammarPodcastServiceTest {

    private static final String PENDING = "gp.podcast_id IS NULL";

    @TempDir Path cache;

    private JdbcTemplate jdbc;
    private NotificationService notifications;
    private HttpClient http;
    private AdminEventService events;
    private GrammarPodcastService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        notifications = mock(NotificationService.class);
        http = mock(HttpClient.class);
        events = mock(AdminEventService.class);
        var props = mock(AppProperties.class);
        when(props.getTtsApiUrl()).thenReturn("http://tts");
        service = new GrammarPodcastService(jdbc, mock(GeminiService.class), props,
            http, new ObjectMapper(), notifications, mock(SchedulerHistoryService.class),
            events, cache.toString());
    }

    private static Map<String, Object> pair(String key, String script) {
        String[] p = key.split(":");
        return Map.of("lesson_id", p[0], "language", p[1], "cefr_level", "A1", "topic", p[0], "script", script);
    }

    /**
     * Each call returns the first pending (lesson, language) not in the skip list and not yet linked to a
     * podcast, like the real query.
     */
    private void pending(String script, String... keys) {
        Set<String> linked = new HashSet<>();
        when(jdbc.update(startsWith("UPDATE grammar_lesson_podcasts SET podcast_id = ?"), any(), any(), any()))
            .thenAnswer(inv -> { linked.add(inv.getArgument(2) + ":" + inv.getArgument(3)); return 1; });
        when(jdbc.queryForList(contains(PENDING), any(Object.class))).thenAnswer(inv -> {
            List<String> skip = Arrays.asList((String[]) inv.getArgument(1));
            return Arrays.stream(keys).filter(k -> !skip.contains(k) && !linked.contains(k))
                .limit(1).map(k -> pair(k, script)).toList();
        });
    }

    @Test
    void failedLessonIsSkippedSoTheRestStillRun() {
        // Empty scripts fail; the run must try each pair once, then stop instead of retrying the first
        pending("[]", "a1-broken:tr", "a1-next:tr");

        service.generateAllPending();

        verify(notifications).error(anyString(), anyString(), contains("a1-broken"));
        verify(notifications).error(anyString(), anyString(), contains("a1-next"));
        verify(jdbc, times(3)).queryForList(contains(PENDING), any(Object.class));
    }

    @Test
    void stopsWhenNothingIsPending() {
        pending("[]");

        service.generateAllPending();

        verify(jdbc, times(1)).queryForList(contains(PENDING), any(Object.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void sameLessonOtherLanguageStillRunsAfterAFailure() {
        pending("[]", "a1-x:en", "a1-x:tr");

        service.generateAllPending();

        verify(notifications, times(2)).error(anyString(), anyString(), contains("a1-x"));
        verify(jdbc, times(3)).queryForList(contains(PENDING), any(Object.class));
    }

    @Test
    void triggerManualClearsOnlyThatLanguage() {
        service.triggerManual("a1-x", "en");

        verify(jdbc).update(contains("grammar_lesson_podcasts SET podcast_id = NULL"), eq("a1-x"), eq("en"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedPodcastIsLinkedToItsLanguage() throws Exception {
        pending("[{\"text\":\"Hello\",\"language\":\"en\"}]", "a1-x:en");
        HttpResponse<byte[]> res = mock(HttpResponse.class);
        when(res.statusCode()).thenReturn(200);
        when(res.body()).thenReturn(new byte[44]);
        when(res.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) res);

        service.generateAllPending();

        verify(jdbc).update(eq("UPDATE grammar_lesson_podcasts SET podcast_id = ? WHERE lesson_id = ? AND language = ?"),
            anyString(), eq("a1-x"), eq("en"));
        verify(jdbc).update(contains("INSERT INTO podcasts"), any(), eq("Grammar: a1-x"), any(), any(), any(), any(), any());
        verify(events).publish(eq("podcast"), argThat(m -> "en".equals(((Map<String, Object>) m).get("language"))
            && "ready".equals(((Map<String, Object>) m).get("status"))));
    }

    @Test
    void statusCountsEachLanguage() {
        when(jdbc.queryForList(contains("scheduler_status"), eq("grammar-podcast"))).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM grammar_lessons g"))).thenReturn(List.of(
            Map.of("language", "tr", "total", 53L, "completed", 53L),
            Map.of("language", "en", "total", 53L, "completed", 10L)));

        var status = service.getStatus();

        @SuppressWarnings("unchecked")
        var en = (Map<String, Object>) ((Map<String, Object>) status.get("byLanguage")).get("en");
        assertEquals(53, en.get("total"));
        assertEquals(10, en.get("completed"));
        assertEquals(43, en.get("pending"));
        // The old admin reads the top-level counts: they stay Turkish
        assertEquals(53, status.get("completedLessons"));
        assertEquals(0, status.get("pendingLessons"));
    }
}
