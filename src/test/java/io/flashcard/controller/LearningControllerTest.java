package io.flashcard.controller;

import io.flashcard.filter.AuthFilter;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.AuthService;
import io.flashcard.service.GrammarBankService;
import io.flashcard.service.LearnerModelService;
import io.flashcard.service.LearningPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningControllerTest {

    @Test
    void planLinksPodcastsInTheLearnersLanguage() throws Exception {
        var plans = mock(LearningPlanService.class);
        var bank = mock(GrammarBankService.class);
        var subscribers = mock(SubscriberRepository.class);
        var prefs = UserPreferences.defaults();
        prefs.setNativeLanguage("english");
        when(subscribers.getPreferences(12345L)).thenReturn(prefs);
        Map<String, Map<String, Object>> info = Map.of("a1-x", Map.of("podcastUrl", "u"));
        when(bank.getPodcastInfo("en")).thenReturn(info);
        when(plans.plan(anyLong(), anyInt(), any())).thenReturn(Map.of());
        var mvc = MockMvcBuilders.standaloneSetup(
                new LearningController(mock(LearnerModelService.class), plans, bank, subscribers))
            .addFilters(new AuthFilter(mock(AuthService.class))).build();

        mvc.perform(get("/api/learning/plan").header("X-User-Id", "12345")).andExpect(status().isOk());

        verify(plans).plan(12345L, 6, info);
    }
}
