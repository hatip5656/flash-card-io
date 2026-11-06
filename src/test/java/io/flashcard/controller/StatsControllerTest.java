package io.flashcard.controller;

import io.flashcard.repository.*;
import io.flashcard.service.DailyGoalService;
import io.flashcard.service.WordBankService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatsControllerTest {

    private static final long USER = 42L;

    @Test
    void streakComesFromDailyGoal() throws Exception {
        ActivityRepository activity = mock(ActivityRepository.class);
        QuizRepository quiz = mock(QuizRepository.class);
        SentWordRepository sentWords = mock(SentWordRepository.class);
        WordBankService wordBank = mock(WordBankService.class);
        DailyGoalService goals = mock(DailyGoalService.class);
        Map<String, Object> stats = new HashMap<>();
        stats.put("level", "A2");
        stats.put("sent", 121);
        when(activity.getStats(USER)).thenReturn(stats);
        when(activity.getStreak(USER)).thenReturn(9);
        when(activity.getTodayActivity(USER)).thenReturn(Map.of("wordsLearned", 0, "quizzesTaken", 0, "gamesPlayed", 0,
            "phrasesSeen", 0, "storiesRead", 0, "dialogsCompleted", 0));
        when(quiz.getQuizStats(USER)).thenReturn(Map.of("total", 0, "avg_pct", 0));
        when(sentWords.getWordCounts(USER)).thenReturn(Map.of("seen", 0, "mastered", 0, "quizzed", 0, "total_quiz_answers", 0,
            "total_seen_count", 0, "total_crush_finds", 0));
        when(sentWords.getLevelReadiness(eq(USER), any())).thenReturn(Map.of("strong", 0, "avg_ease", 2.5));
        when(wordBank.getWordsForLevel(any())).thenReturn(List.of());
        when(wordBank.getAllWords()).thenReturn(List.of());
        when(goals.streak(USER)).thenReturn(4);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StatsController(activity, quiz, sentWords,
            mock(SavedWordRepository.class), wordBank, goals)).build();

        mvc.perform(get("/api/users/me/stats").requestAttr("userId", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.streak").value(4));
    }
}
