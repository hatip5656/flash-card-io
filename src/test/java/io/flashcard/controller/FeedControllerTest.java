package io.flashcard.controller;

import io.flashcard.repository.*;
import io.flashcard.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedControllerTest {

    private static final long USER = 42L;

    private MockMvc mvc;
    private SentWordRepository sentWords;
    private DailyGoalService goals;

    @BeforeEach
    void setUp() {
        sentWords = mock(SentWordRepository.class);
        goals = mock(DailyGoalService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FeedController(mock(SubscriberRepository.class), sentWords,
            mock(SavedWordRepository.class), mock(ActivityRepository.class), mock(WordBankService.class),
            mock(WordDbRepository.class), mock(ImageService.class), mock(LearnerModelService.class),
            mock(LearningSettings.class), goals)).build();
    }

    @Test
    void newWordSeenCountsTowardGoal() throws Exception {
        when(sentWords.markWordSent(eq(USER), eq("w1"), any(), any())).thenReturn(true);

        mvc.perform(post("/api/feed/seen/w1").requestAttr("userId", USER).contentType("application/json")
                .content("{\"estonian\":\"maja\",\"english\":\"house\"}"))
            .andExpect(status().isOk());

        verify(goals).onProgress(USER);
    }

    @Test
    void repeatSeenLeavesGoalAlone() throws Exception {
        when(sentWords.markWordSent(eq(USER), eq("w1"), any(), any())).thenReturn(false);

        mvc.perform(post("/api/feed/seen/w1").requestAttr("userId", USER).contentType("application/json").content("{}"))
            .andExpect(status().isOk());

        verify(goals, never()).onProgress(anyLong());
    }
}
