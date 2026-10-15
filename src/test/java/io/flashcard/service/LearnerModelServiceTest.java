package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.model.GrammarLesson;
import io.flashcard.model.Word;
import io.flashcard.repository.LearningRepository;
import io.flashcard.repository.SentWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearnerModelServiceTest {

    private LearningRepository learningRepo;
    private SentWordRepository sentWordRepo;
    private ExamBankService examBank;
    private WordBankService wordBank;
    private LearnerModelService service;

    private static final long USER = 42L;

    @BeforeEach
    void setUp() {
        learningRepo = mock(LearningRepository.class);
        when(learningRepo.getMastery(anyLong(), anyString())).thenReturn(null);
        sentWordRepo = mock(SentWordRepository.class);
        examBank = mock(ExamBankService.class);
        wordBank = mock(WordBankService.class);
        GrammarBankService grammarBank = mock(GrammarBankService.class);
        when(grammarBank.getAllLessons()).thenReturn(List.of(
            new GrammarLesson("a1-partitive-case", "A1", "Partitive", "Bölümleme", "c", "c")));
        Word apteek = new Word();
        apteek.setId("w1");
        apteek.setEstonian("apteek");
        apteek.setEnglish("pharmacy");
        when(wordBank.getAllWords()).thenReturn(List.of(apteek));
        service = new LearnerModelService(learningRepo, sentWordRepo, examBank, grammarBank, wordBank, new ObjectMapper());

        var q0 = new ExamBankService.Question(0, "Esimene ülesanne – Kuulutused", null, "Kus on apteek?",
            List.of("A", "B", "C"), 1, "");
        var q1 = new ExamBankService.Question(1, "Teine ülesanne", "text", "Mis kell?", List.of("A", "B"), 0, "");
        var exam = new ExamBankService.Exam("a2-x", "A2", "X", "Harno", List.of(q0, q1), Map.of(
            0, new ExamBankService.Tag("vocabulary", List.of(), List.of("apteek")),
            1, new ExamBankService.Tag("grammar", List.of("a1-partitive-case"), List.of())));
        when(examBank.getExam("a2-x")).thenReturn(exam);
    }

    @Test
    void masteryStartsAtHalfAndWeightsRecentAnswers() {
        assertEquals(0.75, LearnerModelService.nextMastery(0.5, 1, true), 1e-9);
        assertEquals(0.25, LearnerModelService.nextMastery(0.5, 1, false), 1e-9);
        // After many attempts alpha floors at 0.2
        assertEquals(0.2, LearnerModelService.nextMastery(0.0, 50, true), 1e-9);
        assertEquals(1, LearnerModelService.recheckDays(0.3));
        assertEquals(3, LearnerModelService.recheckDays(0.7));
        assertEquals(7, LearnerModelService.recheckDays(0.9));
    }

    @Test
    void examQuestionIsGradedOnTheServerNotByTheClient() {
        var results = service.recordEvents(USER, List.of(Map.of(
            "itemType", "exam_question", "itemId", "a2-x#0", "userAnswer", "2", "isCorrect", true)));
        assertEquals(1, results.size());
        assertEquals(false, results.get(0).get("isCorrect"));
        assertEquals(1, results.get(0).get("correctIndex"));
    }

    @Test
    void missedExamQuestionSendsKeyWordsBackToReview() {
        service.recordEvents(USER, List.of(Map.of("itemType", "exam_question", "itemId", "a2-x#0", "userAnswer", "0")));
        verify(sentWordRepo).markWordSent(USER, "w1", "apteek", "pharmacy");
        verify(sentWordRepo).updateSm2(USER, "apteek", 1);
    }

    @Test
    void skippedExamQuestionTouchesNeitherReviewNorMastery() {
        service.recordEvents(USER, List.of(Map.of("itemType", "exam_question", "itemId", "a2-x#1", "userAnswer", "")));
        verifyNoInteractions(sentWordRepo);
        verify(learningRepo, never()).saveMastery(anyLong(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyDouble(), anyInt());
    }

    @Test
    void practiceUpdatesMasteryOnlyForRealLessons() {
        service.recordEvents(USER, List.of(
            Map.of("itemType", "practice_question", "grammarLessonId", "a1-partitive-case", "isCorrect", false),
            Map.of("itemType", "practice_question", "grammarLessonId", "grammar-story-7", "isCorrect", false),
            Map.of("itemType", "not_a_type")));
        verify(learningRepo, times(1)).saveMastery(eq(USER), eq("a1-partitive-case"), eq(1), eq(0), eq(0), eq(0),
            eq(0.25), eq(1));
        verify(learningRepo).insertEvents(eq(USER), argThat(events -> events.size() == 2));
    }

    @Test
    void fullExamIsScoredBySectionSkillAndRule() {
        var result = service.gradeExam(USER, "a2-x", Arrays.asList(1, 1), 600);
        assertEquals(1, result.get("score"));
        assertEquals(2, result.get("total"));
        assertEquals(50L, result.get("percentage"));
        assertEquals(false, result.get("passed"));
        @SuppressWarnings("unchecked")
        var sections = (List<Map<String, Object>>) result.get("sections");
        assertEquals("Esimene ülesanne", sections.get(0).get("name"));
        @SuppressWarnings("unchecked")
        var weak = (List<Map<String, Object>>) result.get("weakLessons");
        assertEquals("a1-partitive-case", weak.get(0).get("lessonId"));
        verify(learningRepo).saveExamAttempt(eq(USER), eq("a2-x"), eq(1), eq(2), eq("[1,1]"), anyString(), eq(600));
        verify(learningRepo).saveMastery(eq(USER), eq("a1-partitive-case"), eq(1), eq(0), eq(1), eq(1), anyDouble(), anyInt());
    }

    @Test
    void parseQuestionsMatchesTheAppNormalisation() {
        var bank = new ExamBankService(mock(JdbcTemplate.class), new ObjectMapper());
        var qs = bank.parseQuestions("""
            [{"prompt":"p1","options":[{"label":"A","text":"x"},{"label":"B","text":"y"}],"answer":"B"},
             {"prompt":"","options":["a","b"],"correctIndex":1},
             {"question":"p3","options":["a","b","c"],"correctIndex":7,"taskInstruction":"read"}]
            """);
        assertEquals(2, qs.size());
        assertEquals("B. y", qs.get(0).options().get(1));
        assertEquals(1, qs.get(0).correctIndex());
        // Blank prompt is dropped; the others keep their stored position
        assertEquals(2, qs.get(1).index());
        assertEquals(0, qs.get(1).correctIndex());
        assertEquals("read", qs.get(1).context());
    }
}
