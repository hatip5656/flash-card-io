package io.flashcard.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ExamScoringTest {

    // Same shape as the TESTEST screening rows: official gate, our placement bands.
    private static final Map<String, Object> SCREENING = Map.of(
        "type", "screening",
        "gate", Map.of("section", "1. osa", "minScore", 35),
        "placement", List.of(
            Map.of("level", "A1", "recommendedLevel", "A2", "ranges", Map.of("1. osa", List.of(0, 19))),
            Map.of("level", "A2", "ranges", Map.of("1. osa", List.of(20, 34))),
            Map.of("level", "B1", "ranges", Map.of("2. osa", List.of(0, 9))),
            Map.of("level", "B2", "ranges", Map.of("2. osa", List.of(10, 17))),
            Map.of("level", "C1", "ranges", Map.of("2. osa", List.of(18, 25)))));

    private static Map<String, int[]> sections(int part1, int part2) {
        Map<String, int[]> s = new LinkedHashMap<>();
        s.put("1. osa", new int[]{part1, 50});
        s.put("2. osa", new int[]{part2, 25});
        return s;
    }

    private static Object level(int part1, int part2) {
        return ExamScoring.verdict(SCREENING, sections(part1, part2), part1 + part2, 75).get("level");
    }

    @Test
    void screeningBelowTheGateIgnoresPartTwo() {
        assertEquals("A1", level(12, 25));
        assertEquals("A2", level(34, 25));
        var v = ExamScoring.verdict(SCREENING, sections(30, 20), 50, 75);
        assertEquals(false, ((Map<?, ?>) v.get("gate")).get("passed"));
    }

    @Test
    void screeningPastTheGatePlacesByPartTwo() {
        assertEquals("B1", level(35, 0));
        assertEquals("B2", level(44, 10));
        assertEquals("C1", level(50, 18));
    }

    @Test
    void placementRulesNameTheLevelToContinueAt() {
        assertEquals("A2", ExamScoring.verdict(SCREENING, sections(5, 0), 5, 75).get("recommendedLevel"));
        assertEquals("B2", ExamScoring.verdict(SCREENING, sections(40, 12), 52, 75).get("recommendedLevel"));
    }

    @Test
    void levelCheckPassingPointsOneLevelUp() {
        var scoring = Map.<String, Object>of("type", "level_check", "level", "B1", "passMark", 0.6, "nextLevel", "B2");
        var passed = ExamScoring.verdict(scoring, Map.of(), 18, 30);
        assertEquals(true, passed.get("passed"));
        assertEquals("B2", passed.get("recommendedLevel"));
        var failed = ExamScoring.verdict(scoring, Map.of(), 17, 30);
        assertEquals(false, failed.get("passed"));
        assertEquals("B1", failed.get("recommendedLevel"));
        var top = ExamScoring.verdict(Map.of("type", "level_check", "level", "C1"), Map.of(), 30, 30);
        assertEquals("C1", top.get("recommendedLevel"));
    }

    @Test
    void examsWithoutRulesHaveNoVerdict() {
        assertNull(ExamScoring.verdict(Map.of(), Map.of(), 1, 2));
    }

    @Test
    void typedAnswersIgnoreCaseSpacingAndOuterPunctuation() {
        var q = new ExamBankService.Question(0, "1. ülesanne", null, "Lünk 1", List.of(), -1, "",
            ExamBankService.TYPE_TEXT_GAP, List.of("kapsas", "kapsast"));
        assertTrue(q.isCorrect("Kapsast."));
        assertTrue(q.isCorrect("  kapsas "));
        assertFalse(q.isCorrect("kapsa"));
        assertFalse(q.isCorrect(""));
        assertFalse(q.isCorrect(null));
        var two = new ExamBankService.Question(1, "", null, "x", List.of(), -1, "",
            ExamBankService.TYPE_TEXT_GAP, List.of("kolmveerand üks"));
        assertTrue(two.isCorrect("Kolmveerand   üks"));
    }

    @Test
    void choiceAnswersAcceptIndexesAsNumbersOrStrings() {
        var q = new ExamBankService.Question(0, "", null, "x", List.of("a", "b"), 1, "");
        assertTrue(q.isCorrect(1));
        assertTrue(q.isCorrect("1"));
        assertFalse(q.isCorrect(0));
        assertFalse(q.isCorrect("b"));
    }
}
