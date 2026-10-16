package io.flashcard.service;

import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.GrammarRepository;
import io.flashcard.repository.LearningRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class LearningPlanRankingTest {

    // The ranking weights as seeded in learning_settings
    private static Map<String, Object> seeded() {
        Map<String, Object> s = new HashMap<>(Map.of(
            "plan.level_order", List.of("A1", "A2", "B1", "B2", "C1"),
            "grammar.weak_below", 0.6, "grammar.practice_weak_min_attempts", 3,
            "ranking.exam_weak_base", 4, "ranking.practice_weak_base", 3, "ranking.recheck_base", 2,
            "ranking.untested_base", 1.5, "ranking.next_up_base", 1, "ranking.weakness_weight", 1));
        s.put("ranking.earlier_level_bonus", 0.1);
        s.put("ranking.teaching_order_step", 0.001);
        return s;
    }

    private static List<String> rank(Map<String, Object> settings) {
        var grammarRepo = mock(GrammarRepository.class);
        when(grammarRepo.getSentGrammarIds(anyLong())).thenReturn(Set.of());
        var learningRepo = mock(LearningRepository.class);
        // Missed in an exam, mastery 0.3: exam_weak
        when(learningRepo.getAllMastery(anyLong())).thenReturn(List.of(Map.of(
            "grammar_lesson_id", "weak", "mastery", 0.3, "attempts", 2, "exam_misses", 1, "due", false)));
        var grammarBank = mock(GrammarBankService.class);
        when(grammarBank.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("weak", "A1", "Weak", "Weak", "c", "c"),
            new GrammarLesson("fresh", "A1", "Fresh", "Fresh", "c", "c")));
        var service = new LearningPlanService(mock(SubscriberRepository.class), grammarRepo, learningRepo,
            mock(SentWordRepository.class), grammarBank, mock(WordBankService.class), mock(GrammarPracticeService.class),
            mock(ExamBankService.class), LearningSettings.of(settings));
        return service.rankLessons(1L, "A2").stream().map(c -> c.lesson().id() + ":" + c.reason()).toList();
    }

    @Test
    void rankingFollowsTheStoredWeights() {
        assertEquals(List.of("weak:exam_weak", "fresh:next_up"), rank(seeded()));

        var nextUpFirst = seeded();
        nextUpFirst.put("ranking.next_up_base", 10);
        assertEquals(List.of("fresh:next_up", "weak:exam_weak"), rank(nextUpFirst));
    }
}
