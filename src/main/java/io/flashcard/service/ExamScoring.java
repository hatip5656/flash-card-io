package io.flashcard.service;

import java.util.*;

/**
 * Turns per-section scores into a verdict, using the rules stored on the exam (official_exams.scoring).
 *
 * <ul>
 *   <li>{@code screening}: a gate ({section, minScore}) below which later sections do not count, and
 *       placement rules [{level, ranges: {section: [min, max]}}]; the first rule whose ranges all hold
 *       gives the estimated level.</li>
 *   <li>{@code level_check}: the exam checks one level; at or above passMark the learner is ready to
 *       try the next level, below it they should keep working at this one.</li>
 * </ul>
 */
public final class ExamScoring {

    static final List<String> LEVELS = List.of("A1", "A2", "B1", "B2", "C1");

    private ExamScoring() {}

    /** Null when the exam has no rules beyond its score. */
    public static Map<String, Object> verdict(Map<String, Object> scoring, Map<String, int[]> sections,
                                              int score, int total) {
        if (scoring == null) return null;
        Object type = scoring.get("type");
        if ("screening".equals(type)) return screening(scoring, sections);
        if ("level_check".equals(type)) return levelCheck(scoring, score, total);
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> screening(Map<String, Object> scoring, Map<String, int[]> sections) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "screening");
        boolean gatePassed = true;
        if (scoring.get("gate") instanceof Map<?, ?> gate) {
            String section = String.valueOf(gate.get("section"));
            int min = ((Number) gate.get("minScore")).intValue();
            int got = sections.getOrDefault(section, new int[2])[0];
            gatePassed = got >= min;
            result.put("gate", Map.of("section", section, "minScore", min, "score", got, "passed", gatePassed));
        }
        String level = null;
        if (scoring.get("placement") instanceof List<?> rules) {
            for (Object r : rules) {
                Map<String, Object> rule = (Map<String, Object>) r;
                Map<String, Object> ranges = (Map<String, Object>) rule.getOrDefault("ranges", Map.of());
                boolean matches = true;
                for (var e : ranges.entrySet()) {
                    List<?> range = (List<?>) e.getValue();
                    int got = sections.getOrDefault(e.getKey(), new int[2])[0];
                    if (got < ((Number) range.get(0)).intValue() || got > ((Number) range.get(1)).intValue()) {
                        matches = false;
                        break;
                    }
                }
                // Rules about a section past the gate only apply when the gate was passed.
                if (matches && !gatePassed && !onlyGateSection(ranges, scoring)) matches = false;
                if (matches) {
                    level = String.valueOf(rule.get("level"));
                    break;
                }
            }
        }
        result.put("level", level);
        result.put("recommendedLevel", recommendedLevel(level));
        return result;
    }

    private static boolean onlyGateSection(Map<String, Object> ranges, Map<String, Object> scoring) {
        if (!(scoring.get("gate") instanceof Map<?, ?> gate)) return true;
        String section = String.valueOf(gate.get("section"));
        return ranges.keySet().stream().allMatch(section::equals);
    }

    /** Diagnostics and exams start at A2: an A1 estimate is pointed at the A2 checks. */
    static String recommendedLevel(String level) {
        if (level == null) return null;
        return "A1".equals(level) ? "A2" : level;
    }

    static Map<String, Object> levelCheck(Map<String, Object> scoring, int score, int total) {
        String level = String.valueOf(scoring.get("level"));
        double passMark = scoring.get("passMark") instanceof Number n ? n.doubleValue() : 0.6;
        boolean passed = total > 0 && (double) score / total >= passMark;
        int i = LEVELS.indexOf(level);
        String next = passed && i >= 0 && i + 1 < LEVELS.size() ? LEVELS.get(i + 1) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "level_check");
        result.put("level", level);
        result.put("passMark", passMark);
        result.put("passed", passed);
        // Passed: try the next level up (null at the top). Not passed: keep practising this level.
        result.put("recommendedLevel", passed ? (next != null ? next : level) : level);
        return result;
    }
}
