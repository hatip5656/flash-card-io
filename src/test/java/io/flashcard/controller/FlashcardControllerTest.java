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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FlashcardControllerTest {

    private MockMvc mvc;
    private SubscriberRepository subscriberRepo;
    private SentWordRepository sentWordRepo;
    private GrammarRepository grammarRepo;
    private GrammarBankService grammarBankService;

    private static final String AUTH = "12345";

    @BeforeEach
    void setUp() {
        subscriberRepo = mock(SubscriberRepository.class);
        sentWordRepo = mock(SentWordRepository.class);
        grammarRepo = mock(GrammarRepository.class);
        grammarBankService = mock(GrammarBankService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new FlashcardController(subscriberRepo, sentWordRepo, grammarRepo, grammarBankService))
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
}
