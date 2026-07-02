package io.flashcard.controller;

import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.GrammarRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.GrammarBankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FlashcardController.class)
@AutoConfigureMockMvc(addFilters = false)
class FlashcardControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private SubscriberRepository subscriberRepo;
    @MockitoBean private SentWordRepository sentWordRepo;
    @MockitoBean private GrammarRepository grammarRepo;
    @MockitoBean private GrammarBankService grammarBankService;

    @Test
    void getNextFlashcardReturns503() throws Exception {
        mvc.perform(get("/api/flashcards/next").requestAttr("userId", 12345L))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getAudioReturns404() throws Exception {
        mvc.perform(get("/api/flashcards/audio/latest").requestAttr("userId", 12345L))
            .andExpect(status().isNotFound());
    }

    @Test
    void getGrammarCardReturnsLesson() throws Exception {
        when(subscriberRepo.getSubscriberLevel(12345L)).thenReturn("A1");
        when(grammarRepo.getSentGrammarIds(12345L)).thenReturn(Set.of());
        when(grammarBankService.getRandomLesson("A1", Set.of())).thenReturn(
            new GrammarLesson("lesson-1", "A1", "Verb 'olema'", "<b>olema</b> = to be"));

        mvc.perform(get("/api/flashcards/grammar").requestAttr("userId", 12345L))
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

        mvc.perform(get("/api/flashcards/grammar").requestAttr("userId", 12345L))
            .andExpect(status().isNotFound());
    }

    @Test
    void getDueWordsReturnsList() throws Exception {
        when(sentWordRepo.getWordsDueForReview(12345L, 10)).thenReturn(List.of(
            Map.of("word_id", "a1-tere", "word_value", "tere", "english", "hello")));

        mvc.perform(get("/api/review/due").requestAttr("userId", 12345L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].word_value").value("tere"));
    }

    @Test
    void submitRecallUpdates() throws Exception {
        mvc.perform(post("/api/review/recall")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wordValue\": \"tere\", \"quality\": 4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true));

        verify(sentWordRepo).updateSm2(12345L, "tere", 4);
    }

    @Test
    void submitRecallRejectsMissingWordValue() throws Exception {
        mvc.perform(post("/api/review/recall")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quality\": 4}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submitRecallRejectsInvalidQuality() throws Exception {
        mvc.perform(post("/api/review/recall")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wordValue\": \"tere\", \"quality\": 6}"))
            .andExpect(status().isBadRequest());
    }
}
