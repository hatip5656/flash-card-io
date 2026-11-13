package io.flashcard.controller;

import io.flashcard.filter.AuthFilter;
import io.flashcard.model.GrammarLesson;
import io.flashcard.model.UserPreferences;
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
    private io.flashcard.service.DailyGoalService goals;

    private static final String AUTH = "12345";

    @BeforeEach
    void setUp() {
        subscriberRepo = mock(SubscriberRepository.class);
        sentWordRepo = mock(SentWordRepository.class);
        grammarRepo = mock(GrammarRepository.class);
        grammarBankService = mock(GrammarBankService.class);
        jdbc = mock(JdbcTemplate.class);
        goals = mock(io.flashcard.service.DailyGoalService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new FlashcardController(subscriberRepo, sentWordRepo, grammarRepo, grammarBankService, jdbc, goals))
            .addFilters(new AuthFilter(org.mockito.Mockito.mock(io.flashcard.service.AuthService.class)))
            .build();
        when(subscriberRepo.getPreferences(anyLong())).thenReturn(UserPreferences.defaults());
    }

    private void learnerLanguage(String nativeLanguage) {
        var prefs = UserPreferences.defaults();
        prefs.setNativeLanguage(nativeLanguage);
        when(subscriberRepo.getPreferences(12345L)).thenReturn(prefs);
    }

    private static Map<String, Object> mediaRow(String lessonId, String podcastId, String audioKey) {
        var row = new HashMap<String, Object>();
        row.put("lesson_id", lessonId);
        row.put("podcast_id", podcastId);
        row.put("audio_cache_key", audioKey);
        row.put("duration_seconds", audioKey == null ? null : 60);
        row.put("podcast_timings", audioKey == null ? null : "[{\"text\":\"Hi\",\"language\":\"en\",\"start_ms\":0,\"end_ms\":900}]");
        row.put("video_url", audioKey == null ? null : "https://videos.example/" + podcastId + ".mp4");
        return row;
    }

    private static Map<String, Map<String, Object>> podcastInfo(String lessonId, String podcastId) {
        return Map.of(lessonId, Map.of("podcastId", podcastId,
            "podcastUrl", "https://wordagram.hatip.dev/podcasts/" + podcastId + ".wav",
            "durationSeconds", 90, "hasVideo", true));
    }

    @Test
    void lessonMediaUsesEnglishForEnglishLearner() throws Exception {
        learnerLanguage("english");
        when(jdbc.queryForList(anyString(), eq("en"), eq("a1-x"))).thenReturn(List.of(mediaRow("a1-x", "pod-en", "pod-en.wav")));

        mvc.perform(get("/api/grammar/lessons/a1-x/media").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.podcastId").value("pod-en"))
            .andExpect(jsonPath("$.videoUrl").value("https://videos.example/pod-en.mp4"));
    }

    @Test
    void lessonMediaUsesTurkishForTurkishLearner() throws Exception {
        learnerLanguage("turkish");
        when(jdbc.queryForList(anyString(), eq("tr"), eq("a1-x"))).thenReturn(List.of(mediaRow("a1-x", "pod-tr", "pod-tr.wav")));

        mvc.perform(get("/api/grammar/lessons/a1-x/media").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.podcastId").value("pod-tr"));
    }

    @Test
    void learnerWithoutSavedLanguageGetsTheAppDefault() throws Exception {
        // No saved language: the app and the server both treat the learner as Turkish, so the media must match
        when(jdbc.queryForList(anyString(), eq("tr"), eq("a1-x"))).thenReturn(List.of(mediaRow("a1-x", "pod-tr", "pod-tr.wav")));

        mvc.perform(get("/api/grammar/lessons/a1-x/media").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.podcastId").value("pod-tr"));
    }

    @Test
    void englishPodcastNotReadyMeansNoPodcast() throws Exception {
        // The English row has no ready podcast yet: nothing is served, never the Turkish one
        learnerLanguage("english");
        when(jdbc.queryForList(anyString(), eq("en"), eq("a1-x"))).thenReturn(List.of(mediaRow("a1-x", null, null)));

        mvc.perform(get("/api/grammar/lessons/a1-x/media").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.podcastUrl").isEmpty())
            .andExpect(jsonPath("$.videoUrl").isEmpty())
            .andExpect(jsonPath("$.subtitles.length()").value(0));
        verify(jdbc, never()).queryForList(anyString(), eq("tr"), eq("a1-x"));
    }

    @Test
    void lessonListUsesLearnerLanguage() throws Exception {
        learnerLanguage("english");
        when(grammarRepo.getSentGrammarIds(12345L)).thenReturn(Set.of());
        when(grammarBankService.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "'olema' fiili", "c", "c")));
        when(grammarBankService.getPodcastInfo("en")).thenReturn(podcastInfo("lesson-1", "pod-en"));
        when(jdbc.queryForList(contains("sentence_grammar_links"))).thenReturn(List.of());

        mvc.perform(get("/api/grammar/lessons").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lessons[0].podcastId").value("pod-en"));
    }

    @Test
    void lessonExamplesNeverRepeatASentence() throws Exception {
        learnerLanguage("turkish");
        when(grammarRepo.getSentGrammarIds(12345L)).thenReturn(Set.of());
        when(grammarBankService.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("lesson-1", "A1", "Numbers", "Sayılar", "c", "c")));
        when(grammarBankService.getPodcastInfo("tr")).thenReturn(Map.of());
        // The same sentence twice: stored twice for one word, or linked through another word
        when(jdbc.queryForList(contains("sentence_grammar_links"))).thenReturn(List.of(
            sentenceRow("lesson-1", "Üks aasta on 12 kuud.", "aasta"),
            sentenceRow("lesson-1", "Üks aasta on 12 kuud.", "aasta"),
            sentenceRow("lesson-1", "Mul ei ole aega.", "aeg"),
            sentenceRow("lesson-1", "Üks aasta on 12 kuud.", "kuu")));

        mvc.perform(get("/api/grammar/lessons").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lessons[0].sentences.length()").value(2))
            .andExpect(jsonPath("$.lessons[0].sentences[0].estonian").value("Üks aasta on 12 kuud."))
            .andExpect(jsonPath("$.lessons[0].sentences[1].estonian").value("Mul ei ole aega."));
    }

    private static Map<String, Object> sentenceRow(String lessonId, String estonian, String word) {
        Map<String, Object> row = new HashMap<>();
        row.put("grammar_lesson_id", lessonId);
        row.put("sentence_estonian", estonian);
        row.put("sentence_english", "en");
        row.put("sentence_turkish", "tr");
        row.put("word", word);
        return row;
    }

    @Test
    void grammarFeedUsesLearnerLanguage() throws Exception {
        learnerLanguage("english");
        when(subscriberRepo.getSubscriberLevel(12345L)).thenReturn("A1");
        when(grammarBankService.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "'olema' fiili", "c", "c")));
        when(grammarBankService.getPodcastInfo("en")).thenReturn(podcastInfo("lesson-1", "pod-en"));

        mvc.perform(get("/api/flashcards/grammar-feed").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].podcastUrl").value("https://wordagram.hatip.dev/podcasts/pod-en.wav"));
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
        when(jdbc.queryForList(anyString(), eq("tr"), eq("lesson-1"))).thenReturn(List.of(row));

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
        when(jdbc.queryForList(anyString(), eq("tr"), eq("lesson-2"))).thenReturn(List.of(row));

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
        when(jdbc.queryForList(anyString(), eq("tr"), eq("missing"))).thenReturn(List.of());

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
        when(grammarBankService.getPodcastInfo("tr")).thenReturn(podcastInfo("lesson-1", "pod-1"));
        when(jdbc.queryForList(contains("sentence_grammar_links"))).thenReturn(List.of());

        mvc.perform(get("/api/grammar/lessons").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lessons[0].podcastId").value("pod-1"))
            .andExpect(jsonPath("$.lessons[0].hasVideo").value(true))
            .andExpect(jsonPath("$.lessons[0].podcastUrl").value("https://wordagram.hatip.dev/podcasts/pod-1.wav"))
            .andExpect(jsonPath("$.lessons[1].podcastId").isEmpty())
            .andExpect(jsonPath("$.lessons[1].hasVideo").value(false));

        // One batched podcast lookup regardless of lesson count (no N+1)
        verify(grammarBankService, times(1)).getPodcastInfo("tr");
    }

    @Test
    void getGrammarFeedIncludesHasVideoFromBatchedQuery() throws Exception {
        when(subscriberRepo.getSubscriberLevel(12345L)).thenReturn("A1");
        when(grammarBankService.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "'olema' fiili", "c", "c"),
            new GrammarLesson("lesson-2", "A1", "Plural", "Çoğul", "c", "c")));
        when(grammarBankService.getPodcastInfo("tr")).thenReturn(podcastInfo("lesson-1", "pod-1"));

        mvc.perform(get("/api/flashcards/grammar-feed").header("X-User-Id", AUTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].podcastUrl").value("https://wordagram.hatip.dev/podcasts/pod-1.wav"))
            .andExpect(jsonPath("$[0].hasVideo").value(true))
            .andExpect(jsonPath("$[1].podcastUrl").doesNotExist())
            .andExpect(jsonPath("$[1].hasVideo").value(false));

        verify(grammarBankService, times(1)).getPodcastInfo("tr");
    }

    @Test
    void readingGrammarLessonCountsTowardGoal() throws Exception {
        mvc.perform(post("/api/flashcards/grammar-feed/a2-pron/read").header("X-User-Id", AUTH))
            .andExpect(status().isOk());
        verify(grammarRepo).markGrammarSent(12345L, "a2-pron");
        verify(goals).onProgress(12345L);
    }
}
