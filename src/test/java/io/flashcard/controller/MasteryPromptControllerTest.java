package io.flashcard.controller;

import io.flashcard.repository.SentWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MasteryPromptControllerTest {

    private static final long USER = 42L;

    private MockMvc mvc;
    private SentWordRepository words;

    @BeforeEach
    void setUp() {
        words = mock(SentWordRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new MasteryPromptController(words)).build();
    }

    @Test
    void listsWordsWaitingForTheQuestion() throws Exception {
        when(words.masteryPrompts(USER, 3)).thenReturn(List.of(
            Map.of("wordId", "w1", "estonian", "huvitav", "english", "interesting", "correctCount", 10)));

        mvc.perform(get("/api/users/me/mastery-prompts").requestAttr("userId", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].wordId").value("w1"))
            .andExpect(jsonPath("$[0].estonian").value("huvitav"));
    }

    @Test
    void yesMastersTheWord() throws Exception {
        mvc.perform(post("/api/users/me/mastery-prompts/w1").requestAttr("userId", USER)
                .contentType("application/json").content("{\"mastered\":true}"))
            .andExpect(status().isNoContent());

        verify(words).markWordMastered(USER, "w1");
        verify(words, never()).postponeMasteryPrompt(anyLong(), any(), anyInt());
    }

    @Test
    void notYetAsksAgainAfterTenMoreCorrect() throws Exception {
        mvc.perform(post("/api/users/me/mastery-prompts/w1").requestAttr("userId", USER)
                .contentType("application/json").content("{\"mastered\":false}"))
            .andExpect(status().isNoContent());

        verify(words).postponeMasteryPrompt(USER, "w1", 10);
        verify(words, never()).markWordMastered(anyLong(), any());
    }

    @Test
    void answerIsRequired() throws Exception {
        mvc.perform(post("/api/users/me/mastery-prompts/w1").requestAttr("userId", USER)
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(words);
    }
}
