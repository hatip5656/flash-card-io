package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.service.GrammarBankService;
import io.flashcard.service.GrammarPodcastService;
import io.flashcard.service.PodcastVideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminGrammarVideoTest {

    private static final UUID PODCAST_ID = UUID.fromString("13224fd7-0468-4d91-b332-d73bf8455e18");
    private static final String TIMINGS = """
        [{"text":"Bugün","language":"tr","start_ms":0,"end_ms":2720,"extra":1},
         {"text":"Raamat.","language":"et","start_ms":7880,"end_ms":10120}]
        """;

    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private PodcastVideoService videos;
    private GrammarPodcastService podcasts;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        videos = mock(PodcastVideoService.class);
        podcasts = mock(GrammarPodcastService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminGrammarController(
            jdbc, new ObjectMapper(), podcasts,
            mock(GrammarBankService.class), videos)).build();
    }

    private void lessonRow(Map<String, Object> row) {
        when(jdbc.queryForList(anyString(), eq("a1-plural-formation"))).thenReturn(List.of(row));
    }

    private Map<String, Object> readyPodcast() {
        var row = new HashMap<String, Object>();
        row.put("podcast_id", PODCAST_ID);
        row.put("podcast_status", "ready");
        row.put("audio_cache_key", PODCAST_ID + ".wav");
        row.put("podcast_timings", TIMINGS);
        return row;
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSendsPodcastAudioAndTimings() throws Exception {
        lessonRow(readyPodcast());
        when(videos.request(anyString(), anyString(), anyString(), anyList(), anyBoolean()))
            .thenReturn(Map.of("id", PODCAST_ID.toString(), "status", "queued"));

        mvc.perform(post("/api/admin/grammar/a1-plural-formation/video"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("queued"));

        ArgumentCaptor<List<Map<String, Object>>> subs = ArgumentCaptor.forClass(List.class);
        verify(videos).request(eq("a1-plural-formation"), eq(PODCAST_ID.toString()),
            eq("https://wordagram.hatip.dev/podcasts/" + PODCAST_ID + ".wav"), subs.capture(), eq(false));
        assertEquals(2, subs.getValue().size());
        assertEquals(Map.of("text", "Bugün", "language", "tr", "start_ms", 0, "end_ms", 2720), subs.getValue().get(0));
    }

    @Test
    void generateRejectsLessonWithoutReadyPodcast() throws Exception {
        var row = readyPodcast();
        row.put("podcast_status", "generating");
        lessonRow(row);

        mvc.perform(post("/api/admin/grammar/a1-plural-formation/video"))
            .andExpect(status().isConflict());
        verifyNoInteractions(videos);
    }

    @Test
    void generateReportsServiceOutage() throws Exception {
        lessonRow(readyPodcast());
        when(videos.request(anyString(), anyString(), anyString(), anyList(), anyBoolean()))
            .thenThrow(new IOException("connection refused"));

        mvc.perform(post("/api/admin/grammar/a1-plural-formation/video"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("Video service unavailable: connection refused"));
    }

    @Test
    void statusComesFromTrackedRender() throws Exception {
        lessonRow(readyPodcast());
        when(videos.statusFor(PODCAST_ID.toString())).thenReturn(Map.of(
            "id", PODCAST_ID.toString(), "status", "done",
            "videoUrl", "https://wordagram.hatip.dev/videos/" + PODCAST_ID + ".mp4"));

        mvc.perform(get("/api/admin/grammar/a1-plural-formation/video"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("done"))
            .andExpect(jsonPath("$.videoUrl").value("https://wordagram.hatip.dev/videos/" + PODCAST_ID + ".mp4"));
    }

    @Test
    void bulkRedoQueuesOnlyLessonsWithScripts() throws Exception {
        when(jdbc.queryForList(contains("RETURNING id"), eq(String.class), any(Object.class)))
            .thenReturn(List.of("a1-negation"));

        mvc.perform(post("/api/admin/grammar/redo-podcasts")
                .contentType("application/json").content("{\"ids\":[\"a1-negation\",\"a1-no-script\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queued[0]").value("a1-negation"))
            .andExpect(jsonPath("$.skipped").value(1));
        verify(podcasts, timeout(1000)).generateAllPending();
    }

    @Test
    void bulkRedoRequiresIds() throws Exception {
        mvc.perform(post("/api/admin/grammar/redo-podcasts").contentType("application/json").content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(podcasts);
    }
}
