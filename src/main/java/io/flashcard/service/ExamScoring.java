package io.flashcard.service;

import java.util.*;

/**
 * Turns per-section scores into a verdict, using the rules stored on the exam (official_exams.scoring).
 *
 * <ul>
 *   <li>{@code screening}: a gate ({section, minScore}) below which later sections do not count, and
 *       placement rules [{level, recommendedLevel?, ranges: {section: [min, max]}}]; the first rule whose
 *       ranges all hold gives the estimated level and the level to continue at (the level itself if the
 *       rule names none).</li>
 *   <li>{@code level_check}: the exam checks one level; at or above passMark the learner is pointed at
 *       nextLevel (the same level when there is none), below it at this level.</li>
 * </ul>
 * Every level, band and mark comes from the stored rules; nothing about levels is assumed here.
 */
public final class ExamScoring {

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
        String recommended = null;
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
                    recommended = rule.get("recommendedLevel") != null ? String.valueOf(rule.get("recommendedLevel")) : level;
                    break;
                }
            }
        }
        result.put("level", level);
        result.put("recommendedLevel", recommended);
        return result;
    }

    private static boolean onlyGateSection(Map<String, Object> ranges, Map<String, Object> scoring) {
        if (!(scoring.get("gate") instanceof Map<?, ?> gate)) return true;
        String section = String.valueOf(gate.get("section"));
        return ranges.keySet().stream().allMatch(section::equals);
    }

    static Map<String, Object> levelCheck(Map<String, Object> scoring, int score, int total) {
        String level = String.valueOf(scoring.get("level"));
        double passMark = scoring.get("passMark") instanceof Number n ? n.doubleValue() : 0.6;
        boolean passed = total > 0 && (double) score / total >= passMark;
        String next = scoring.get("nextLevel") != null ? String.valueOf(scoring.get("nextLevel")) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "level_check");
        result.put("level", level);
        result.put("passMark", passMark);
        result.put("passed", passed);
        // Passed: try the next level (this one again at the top). Not passed: keep practising this level.
        result.put("recommendedLevel", passed && next != null ? next : level);
        return result;
    }
}
