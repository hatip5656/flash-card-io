package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.service.GrammarBankService;
import io.flashcard.service.GrammarPodcastService;
import io.flashcard.service.PodcastVideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The admin grammar endpoints work on one language at a time (?lang=tr|en, Turkish by default). */
class AdminGrammarLanguageTest {

    private static final String LESSON = "a1-numbers";
    private static final String TIMINGS = """
        [{"text":"Hello","language":"en","start_ms":0,"end_ms":900},{"text":"Üks","language":"et","start_ms":1000,"end_ms":1600}]
        """;

    @TempDir Path cache;

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
            jdbc, new ObjectMapper(), podcasts, mock(GrammarBankService.class), videos, cache.toString())).build();
    }

    private static Map<String, Object> readyPodcast(String podcastId) {
        var row = new HashMap<String, Object>();
        row.put("podcast_id", podcastId);
        row.put("podcast_status", "ready");
        row.put("audio_cache_key", podcastId + ".wav");
        row.put("podcast_timings", TIMINGS);
        return row;
    }

    @Test
    void videoForEnglishUsesTheEnglishPodcast() throws Exception {
        when(jdbc.queryForList(anyString(), eq("en"), eq(LESSON))).thenReturn(List.of(readyPodcast("pod-en")));
        when(videos.request(anyString(), anyString(), anyString(), anyList(), anyBoolean()))
            .thenReturn(Map.of("id", "pod-en", "status", "queued"));

        mvc.perform(post("/api/admin/grammar/" + LESSON + "/video?lang=en")).andExpect(status().isOk());

        verify(videos).request(eq(LESSON), eq("pod-en"), eq("https://wordagram.hatip.dev/podcasts/pod-en.wav"),
            anyList(), eq(false));
    }

    @Test
    void langDefaultsToTurkish() throws Exception {
        when(jdbc.queryForList(anyString(), eq("tr"), eq(LESSON))).thenReturn(List.of(readyPodcast("pod-tr")));
        when(videos.statusFor("pod-tr")).thenReturn(Map.of("id", "pod-tr", "status", "done"));

        mvc.perform(get("/api/admin/grammar/" + LESSON + "/video"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("pod-tr"));
    }

    @Test
    void badLangIs400() throws Exception {
        for (var req : List.of(
                post("/api/admin/grammar/" + LESSON + "/video?lang=de"),
                get("/api/admin/grammar/" + LESSON + "/video?lang=de"),
                get("/api/admin/grammar/" + LESSON + "?lang=de"),
                post("/api/admin/grammar/" + LESSON + "/redo-podcast?lang=de"),
                delete("/api/admin/grammar/" + LESSON + "/podcast?lang=de"),
                patch("/api/admin/grammar/" + LESSON + "/script?lang=de")
                    .contentType("application/json").content("{\"podcast_script\":[{\"text\":\"x\"}]}"),
                post("/api/admin/grammar/redo-podcasts?lang=de")
                    .contentType("application/json").content("{\"ids\":[\"" + LESSON + "\"]}"))) {
            mvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("lang must be tr or en"));
        }
        verifyNoInteractions(videos, podcasts);
    }

    @Test
    void redoPodcastClearsOnlyThatLanguage() throws Exception {
        mvc.perform(post("/api/admin/grammar/" + LESSON + "/redo-podcast?lang=en")).andExpect(status().isOk());

        verify(jdbc).update(contains("UPDATE grammar_lesson_podcasts SET podcast_id = NULL"), eq(LESSON), eq("en"));
        verify(jdbc, never()).update(anyString(), eq(LESSON), eq("tr"));
        verify(jdbc, never()).update(contains("UPDATE grammar_lessons"), any(Object[].class));
    }

    @Test
    void removePodcastClearsOnlyThatLanguage() throws Exception {
        mvc.perform(delete("/api/admin/grammar/" + LESSON + "/podcast?lang=en")).andExpect(status().isOk());

        verify(jdbc).update(contains("UPDATE grammar_lesson_podcasts SET podcast_id = NULL"), eq(LESSON), eq("en"));
        verify(jdbc, never()).update(anyString(), eq(LESSON), eq("tr"));
    }

    @Test
    void scriptPatchUpsertsTheLanguageRow() throws Exception {
        mvc.perform(patch("/api/admin/grammar/" + LESSON + "/script?lang=en")
                .contentType("application/json")
                .content("{\"podcast_script\":[{\"text\":\"Hello\",\"language\":\"en\",\"pause_after_ms\":500}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segments").value(1));

        verify(jdbc).update(contains("ON CONFLICT (lesson_id, language) DO UPDATE"), eq(LESSON), eq("en"), contains("Hello"));
    }

    @Test
    void detailReturnsTheLanguagesScriptAndPodcast() throws Exception {
        var lesson = new HashMap<String, Object>();
        lesson.put("id", LESSON);
        lesson.put("topic", "Numbers");
        when(jdbc.queryForList(contains("FROM grammar_lessons g WHERE g.id = ?"), eq(LESSON))).thenReturn(List.of(lesson));
        var media = readyPodcast("pod-en");
        media.put("podcast_script", "[{\"text\":\"Hello\",\"language\":\"en\"}]");
        when(jdbc.queryForList(contains("gp.language = ?"), eq("en"), eq(LESSON))).thenReturn(List.of(media));

        mvc.perform(get("/api/admin/grammar/" + LESSON + "?lang=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("Numbers"))
            .andExpect(jsonPath("$.podcast_script[0].text").value("Hello"))
            .andExpect(jsonPath("$.podcast_timings[0].language").value("en"))
            .andExpect(jsonPath("$.audioUrl").value("https://wordagram.hatip.dev/podcasts/pod-en.wav"));
    }

    @Test
    void listReturnsBothLanguages() throws Exception {
        var lesson = new HashMap<String, Object>();
        lesson.put("id", LESSON);
        lesson.put("cefr_level", "A1");
        lesson.put("topic", "Numbers");
        lesson.put("topic_tr", "Sayılar");
        when(jdbc.queryForList(contains("FROM grammar_lessons g ORDER BY"))).thenReturn(List.of(lesson));
        var tr = new HashMap<String, Object>();
        tr.put("lesson_id", LESSON);
        tr.put("language", "tr");
        tr.put("podcast_id", "pod-tr");
        tr.put("has_script", true);
        tr.put("audio_cache_key", "pod-tr.wav");
        tr.put("video_status", "done");
        var en = new HashMap<String, Object>();
        en.put("lesson_id", LESSON);
        en.put("language", "en");
        en.put("podcast_id", "pod-en");
        en.put("has_script", true);
        en.put("audio_cache_key", "pod-en.wav");
        when(jdbc.queryForList(contains("FROM grammar_lesson_podcasts gp"))).thenReturn(List.of(tr, en));
        when(jdbc.queryForList(contains("WHERE v.status IN"))).thenReturn(List.of());

        mvc.perform(get("/api/admin/grammar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].media.tr.podcast_id").value("pod-tr"))
            .andExpect(jsonPath("$.items[0].media.en.podcast_id").value("pod-en"))
            .andExpect(jsonPath("$.items[0].media.en.audioUrl").value("https://wordagram.hatip.dev/podcasts/pod-en.wav"))
            // The current admin reads the top-level fields: they stay Turkish during the rollout
            .andExpect(jsonPath("$.items[0].podcast_id").value("pod-tr"))
            .andExpect(jsonPath("$.items[0].video_status").value("done"))
            .andExpect(jsonPath("$.withPodcast").value(1))
            .andExpect(jsonPath("$.statsByLanguage.en.withPodcast").value(1))
            .andExpect(jsonPath("$.statsByLanguage.en.withScript").value(1));
    }

    @Test
    void lessonWithoutEnglishRowShowsEmptyEnglishMedia() throws Exception {
        var lesson = new HashMap<String, Object>();
        lesson.put("id", LESSON);
        lesson.put("cefr_level", "A1");
        lesson.put("topic", "Numbers");
        when(jdbc.queryForList(contains("FROM grammar_lessons g ORDER BY"))).thenReturn(List.of(lesson));
        when(jdbc.queryForList(contains("FROM grammar_lesson_podcasts gp"))).thenReturn(List.of());
        when(jdbc.queryForList(contains("WHERE v.status IN"))).thenReturn(List.of());

        mvc.perform(get("/api/admin/grammar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].media.en.has_script").value(false))
            .andExpect(jsonPath("$.items[0].media.en.podcast_id").isEmpty())
            .andExpect(jsonPath("$.statsByLanguage.en.pendingGeneration").value(0));
    }

    @Test
    void bulkRedoIsPerLanguage() throws Exception {
        when(jdbc.queryForList(contains("RETURNING lesson_id"), eq(String.class), any(), eq("en")))
            .thenReturn(List.of(LESSON));

        mvc.perform(post("/api/admin/grammar/redo-podcasts?lang=en")
                .contentType("application/json").content("{\"ids\":[\"" + LESSON + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queued[0]").value(LESSON));
    }

    @Test
    void cleanupKeepsEnglishAudio() throws Exception {
        Path dir = Files.createDirectories(cache.resolve("podcasts"));
        Files.writeString(dir.resolve("pod-en.wav"), "x");
        Files.writeString(dir.resolve("orphan.wav"), "x");
        when(jdbc.queryForList(contains("SELECT audio_cache_key FROM podcasts"), eq(String.class)))
            .thenReturn(List.of("pod-en.wav"));

        mvc.perform(post("/api/admin/grammar/cleanup-orphans"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(1));

        assertTrue(Files.exists(dir.resolve("pod-en.wav")));
        assertFalse(Files.exists(dir.resolve("orphan.wav")));
    }
}
