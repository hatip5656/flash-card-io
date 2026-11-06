package io.flashcard.controller;

import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.QuizRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.DailyGoalService;
import io.flashcard.service.WordBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileQuizControllerTest {

    private static final long USER = 42L;

    private MockMvc mvc;
    private SentWordRepository sentWords;
    private DailyGoalService goals;

    @BeforeEach
    void setUp() {
        sentWords = mock(SentWordRepository.class);
        goals = mock(DailyGoalService.class);
        SubscriberRepository subscribers = mock(SubscriberRepository.class);
        when(subscribers.getSubscriberLevel(USER)).thenReturn("B2"); // no level-up check in the response
        mvc = MockMvcBuilders.standaloneSetup(new MobileQuizController(mock(QuizRepository.class), sentWords,
            subscribers, mock(ActivityRepository.class), mock(WordBankService.class), goals)).build();
    }

    // The goal counts a review only if the word was due, so it must look before the answer moves the date
    @Test
    void singleAnswerCountsTowardGoalBeforeReviewUpdate() throws Exception {
        mvc.perform(post("/api/mobile/quiz/answer").requestAttr("userId", USER).contentType("application/json")
                .content("{\"estonian\":\"maja\",\"isCorrect\":true}"))
            .andExpect(status().isOk());

        InOrder order = inOrder(goals, sentWords);
        order.verify(goals).recordQuizAnswers(USER, List.of("maja"));
        order.verify(sentWords).updateSm2(USER, "maja", 4);
    }

    @Test
    void submittedQuizCountsEveryAnswerBeforeReviewUpdate() throws Exception {
        mvc.perform(post("/api/mobile/quiz/submit").requestAttr("userId", USER).contentType("application/json")
                .content("{\"answers\":[{\"estonian\":\"maja\",\"isCorrect\":true},{\"estonian\":\"koer\",\"isCorrect\":false}]}"))
            .andExpect(status().isOk());

        InOrder order = inOrder(goals, sentWords);
        order.verify(goals).recordQuizAnswers(USER, List.of("maja", "koer"));
        order.verify(sentWords, times(2)).updateSm2(eq(USER), anyString(), anyInt());
    }
}
