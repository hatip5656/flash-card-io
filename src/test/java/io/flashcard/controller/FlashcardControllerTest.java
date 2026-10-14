package io.flashcard.controller;

import io.flashcard.filter.AuthFilter;
import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.GrammarRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.GrammarBankService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FlashcardControllerTest {

    private MockMvc mvc;
    private SubscriberRepository subscriberRepo;
    private SentWordRepository sentWordRepo;
    private GrammarRepository grammarRepo;
    private GrammarBankService grammarBankService;
    private JdbcTemplate jdbc;

    private static final String AUTH = "12345";

    @BeforeEach
    void setUp() {
        subscriberRepo = mock(SubscriberRepository.class);
        sentWordRepo = mock(SentWordRepository.class);
        grammarRepo = mock(GrammarRepository.class);
        grammarBankService = mock(GrammarBankService.class);
        jdbc = mock(JdbcTemplate.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new FlashcardController(subscriberRepo, sentWordRepo, grammarRepo, grammarBankService, jdbc))
            .addFilters(new AuthFilter())
            .build();
    }

    @Test
    void getNextFlashcardReturns503() throws Exception {
        mvc.perform(get("/api/flashcards/next").header("X-User-Id", AUTH))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getAudioReturns404() throws Exception {
        mvc.perform(get("/api/flashcards/audio/latest").header("X-User-Id", AUTH))
            .andExpect(status().isNotFound());
    }

    @Test
    void getGrammarCardReturnsLesson() throws Exception {
        when(subscriberRepo.getSubscriberLevel(12345L)).thenReturn("A1");
        when(grammarRepo.getSentGrammarIds(12345L)).thenReturn(Set.of());
        when(grammarBankService.getRandomLesson("A1", Set.of())).thenReturn(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "'olema' fiili", "<b>olema</b> = to be", "<b>olema</b> = olmak"));

        mvc.perform(get("/api/flashcards/grammar").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("lesson-1"))
            .andExpect(jsonPath("$.topic").value("Verb 'olema'"))
            .andExpect(jsonPath("$.cefrLevel").value("A1"))
            .andExpect(jsonPath("$.content").value("<b>olema</b> = to be"));
    }

    @Test
    void getGrammarCardReturns404WhenNone() throws Exception {
        when(subscriberRepo.getSubscriberLevel(12345L)).thenReturn("A1");
        when(grammarRepo.getSentGrammarIds(12345L)).thenReturn(Set.of());
        when(grammarBankService.getRandomLesson("A1", Set.of())).thenReturn(null);

        mvc.perform(get("/api/flashcards/grammar").header("X-User-Id", AUTH))
            .andExpect(status().isNotFound());
    }

    @Test
    void getDueWordsReturnsList() throws Exception {
        when(sentWordRepo.getWordsDueForReview(12345L, 10)).thenReturn(List.of(
            Map.of("word_id", "a1-tere", "word_value", "tere", "english", "hello")));

        mvc.perform(get("/api/review/due").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].word_value").value("tere"));
    }

    @Test
    void submitRecallUpdates() throws Exception {
        mvc.perform(post("/api/review/recall")
                .header("X-User-Id", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wordValue\": \"tere\", \"quality\": 4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true));

        verify(sentWordRepo).updateSm2(12345L, "tere", 4);
    }

    @Test
    void submitRecallRejectsMissingWordValue() throws Exception {
        mvc.perform(post("/api/review/recall")
                .header("X-User-Id", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quality\": 4}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submitRecallRejectsInvalidQuality() throws Exception {
        mvc.perform(post("/api/review/recall")
                .header("X-User-Id", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wordValue\": \"tere\", \"quality\": 6}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getLessonMediaReturnsPodcastVideoAndSubtitles() throws Exception {
        var row = new HashMap<String, Object>();
        row.put("lesson_id", "lesson-1");
        row.put("podcast_id", "pod-1");
        row.put("audio_cache_key", "pod-1.wav");
        row.put("duration_seconds", 120);
        row.put("podcast_timings", """
            [{"text":"<<Tere>> merhaba demek","language":"tr","start_ms":0,"end_ms":1500,"pause_after_ms":500},
             {"text":"Tere","language":"et","start_ms":2000,"end_ms":2600}]
            """);
        row.put("video_url", "https://videos.example/pod-1.mp4");
        when(jdbc.queryForList(anyString(), eq("lesson-1"))).thenReturn(List.of(row));

        mvc.perform(get("/api/grammar/lessons/lesson-1/media").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lessonId").value("lesson-1"))
            .andExpect(jsonPath("$.podcastId").value("pod-1"))
            .andExpect(jsonPath("$.podcastUrl").value("https://wordagram.hatip.dev/podcasts/pod-1.wav"))
            .andExpect(jsonPath("$.durationSeconds").value(120))
            .andExpect(jsonPath("$.videoUrl").value("https://videos.example/pod-1.mp4"))
            .andExpect(jsonPath("$.videoOffsetMs").value(1500))
            .andExpect(jsonPath("$.subtitles.length()").value(2))
            .andExpect(jsonPath("$.subtitles[0].text").value("Tere merhaba demek"))
            .andExpect(jsonPath("$.subtitles[0].language").value("tr"))
            .andExpect(jsonPath("$.subtitles[0].startMs").value(0))
            .andExpect(jsonPath("$.subtitles[0].endMs").value(1500))
            .andExpect(jsonPath("$.subtitles[0].pause_after_ms").doesNotExist())
            .andExpect(jsonPath("$.subtitles[1].language").value("et"))
            .andExpect(jsonPath("$.subtitles[1].startMs").value(2000));
    }

    @Test
    void getLessonMediaWithoutPodcastReturnsNulls() throws Exception {
        var row = new HashMap<String, Object>();
        row.put("lesson_id", "lesson-2");
        row.put("podcast_id", "pod-pending");
        row.put("audio_cache_key", null);
        row.put("duration_seconds", null);
        row.put("podcast_timings", null);
        row.put("video_url", null);
        when(jdbc.queryForList(anyString(), eq("lesson-2"))).thenReturn(List.of(row));

        mvc.perform(get("/api/grammar/lessons/lesson-2/media").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lessonId").value("lesson-2"))
            .andExpect(jsonPath("$.podcastId").isEmpty())
            .andExpect(jsonPath("$.podcastUrl").isEmpty())
            .andExpect(jsonPath("$.videoUrl").isEmpty())
            .andExpect(jsonPath("$.videoOffsetMs").isEmpty())
            .andExpect(jsonPath("$.subtitles.length()").value(0));
    }

    @Test
    void getLessonMediaReturns404ForUnknownLesson() throws Exception {
        when(jdbc.queryForList(anyString(), eq("missing"))).thenReturn(List.of());

        mvc.perform(get("/api/grammar/lessons/missing/media").header("X-User-Id", AUTH))
            .andExpect(status().isNotFound());
    }

    @Test
    void getLessonMediaRequiresAuth() throws Exception {
        mvc.perform(get("/api/grammar/lessons/lesson-1/media"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllGrammarLessonsIncludesPodcastIdAndHasVideo() throws Exception {
        when(grammarRepo.getSentGrammarIds(12345L)).thenReturn(Set.of());
        when(grammarBankService.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "'olema' fiili", "c", "c"),
            new GrammarLesson("lesson-2", "A1", "Plural", "Çoğul", "c", "c")));
        var row = new HashMap<String, Object>();
        row.put("lesson_id", "lesson-1");
        row.put("podcast_id", "pod-1");
        row.put("audio_cache_key", "pod-1.wav");
        row.put("duration_seconds", 90);
        row.put("has_video", true);
        when(jdbc.queryForList(contains("podcast_videos"))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("sentence_grammar_links"))).thenReturn(List.of());

        mvc.perform(get("/api/grammar/lessons").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lessons[0].podcastId").value("pod-1"))
            .andExpect(jsonPath("$.lessons[0].hasVideo").value(true))
            .andExpect(jsonPath("$.lessons[0].podcastUrl").value("https://wordagram.hatip.dev/podcasts/pod-1.wav"))
            .andExpect(jsonPath("$.lessons[1].podcastId").isEmpty())
            .andExpect(jsonPath("$.lessons[1].hasVideo").value(false));

        // One batched podcast query regardless of lesson count (no N+1)
        verify(jdbc, times(1)).queryForList(contains("podcast_videos"));
    }

    @Test
    void getGrammarFeedIncludesHasVideoFromBatchedQuery() throws Exception {
        when(subscriberRepo.getSubscriberLevel(12345L)).thenReturn("A1");
        when(grammarBankService.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "'olema' fiili", "c", "c"),
            new GrammarLesson("lesson-2", "A1", "Plural", "Çoğul", "c", "c")));
        var row = new HashMap<String, Object>();
        row.put("lesson_id", "lesson-1");
        row.put("podcast_id", "pod-1");
        row.put("audio_cache_key", "pod-1.wav");
        row.put("duration_seconds", 90);
        row.put("has_video", true);
        when(jdbc.queryForList(contains("podcast_videos"))).thenReturn(List.of(row));

        mvc.perform(get("/api/flashcards/grammar-feed").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].podcastUrl").value("https://wordagram.hatip.dev/podcasts/pod-1.wav"))
            .andExpect(jsonPath("$[0].hasVideo").value(true))
            .andExpect(jsonPath("$[1].podcastUrl").doesNotExist())
            .andExpect(jsonPath("$[1].hasVideo").value(false));

        verify(jdbc, times(1)).queryForList(contains("podcast_videos"));
    }
}
