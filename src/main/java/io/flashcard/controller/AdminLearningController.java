package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.LearningRepository;
import io.flashcard.service.GrammarBankService;
import io.flashcard.service.LearningPlanService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/** Visibility into the learning loop: one learner's goal and progress, and how the loop does overall. */
@RestController
public class AdminLearningController {

    private final JdbcTemplate jdbc;
    private final LearningRepository learningRepo;
    private final LearningPlanService planService;
    private final GrammarBankService grammarBank;
    private final ObjectMapper objectMapper;

    public AdminLearningController(JdbcTemplate jdbc, LearningRepository learningRepo, LearningPlanService planService,
                                   GrammarBankService grammarBank, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.learningRepo = learningRepo;
        this.planService = planService;
        this.grammarBank = grammarBank;
        this.objectMapper = objectMapper;
    }

    /** Goal, readiness, grammar mastery, exam attempts, written answers and recent activity for one learner. */
    @GetMapping("/api/admin/users/{chatId}/learning")
    public Map<String, Object> learner(@PathVariable long chatId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goal", learningRepo.getGoal(chatId));
        out.put("readiness", planService.readiness(chatId));

        Map<String, GrammarLesson> lessons = grammarBank.getAllLessons().stream()
            .collect(Collectors.toMap(GrammarLesson::id, l -> l, (a, b) -> a));
        out.put("mastery", learningRepo.getAllMastery(chatId).stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>(m);
            var lesson = lessons.get((String) m.get("grammar_lesson_id"));
            row.put("topic", lesson != null ? lesson.topic() : m.get("grammar_lesson_id"));
            row.put("cefr_level", lesson != null ? lesson.cefrLevel() : null);
            return row;
        }).sorted(Comparator.comparingDouble(r -> ((Number) r.get("mastery")).doubleValue())).toList());

        out.put("examAttempts", withJson(jdbc.queryForList("""
            SELECT a.id, a.exam_id, e.title, e.kind, e.skill, e.cefr_level, e.plan_role, a.score, a.total,
                   a.duration_seconds, a.completed_at, a.server_graded, a.predicted_score, a.verdict::text AS verdict
            FROM exam_attempts a LEFT JOIN official_exams e ON e.id = a.exam_id
            WHERE a.chat_id = ? ORDER BY a.completed_at DESC LIMIT 50
            """, chatId), "verdict"));

        out.put("taskResponses", jdbc.queryForList("""
            SELECT r.task_set_id, t.title, t.skill, t.cefr_level, r.task_index, r.response, r.created_at
            FROM exam_task_responses r JOIN exam_tasks t ON t.id = r.task_set_id
            WHERE r.chat_id = ? ORDER BY r.created_at DESC LIMIT 50
            """, chatId));

        out.put("activity", jdbc.queryForList("""
            SELECT item_type, source, COUNT(*) AS events,
                   COUNT(*) FILTER (WHERE is_correct) AS correct, COUNT(*) FILTER (WHERE is_correct = FALSE) AS wrong,
                   MAX(created_at) AS last_at
            FROM learning_events WHERE chat_id = ? AND created_at > NOW() - INTERVAL '30 days'
            GROUP BY item_type, source ORDER BY events DESC
            """, chatId));
        return out;
    }

    /** Across learners: goals, placement results, prediction accuracy on goal exams, and loop activity. */
    @GetMapping("/api/admin/learning/insights")
    public Map<String, Object> insights() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goals", jdbc.queryForList(
            "SELECT target_level, source, COUNT(*) AS learners FROM learner_goals GROUP BY target_level, source ORDER BY target_level, source"));

        // Each learner's latest placement result
        out.put("placements", jdbc.queryForList("""
            SELECT level, COUNT(*) AS learners FROM (
              SELECT DISTINCT ON (a.chat_id) a.verdict->>'level' AS level
              FROM exam_attempts a JOIN official_exams e ON e.id = a.exam_id
              WHERE e.kind = 'screening' AND a.verdict IS NOT NULL
              ORDER BY a.chat_id, a.completed_at DESC
            ) latest GROUP BY level ORDER BY level
            """));

        // Predicted (readiness estimate just before) vs actual score on goal exams
        out.put("calibration", jdbc.queryForList("""
            SELECT e.skill, e.cefr_level, COUNT(*) AS attempts,
                   AVG(a.predicted_score) AS avg_predicted,
                   AVG(a.score::float / NULLIF(a.total, 0)) AS avg_actual,
                   AVG(ABS(a.predicted_score - a.score::float / NULLIF(a.total, 0))) AS mean_abs_error,
                   AVG(a.score::float / NULLIF(a.total, 0) - a.predicted_score) AS bias
            FROM exam_attempts a JOIN official_exams e ON e.id = a.exam_id
            WHERE a.predicted_score IS NOT NULL
            GROUP BY e.skill, e.cefr_level ORDER BY e.cefr_level, e.skill
            """));
        out.put("calibrationRecent", jdbc.queryForList("""
            SELECT a.chat_id, a.exam_id, e.title, e.skill, e.cefr_level, a.predicted_score,
                   a.score::float / NULLIF(a.total, 0) AS actual, a.completed_at
            FROM exam_attempts a JOIN official_exams e ON e.id = a.exam_id
            WHERE a.predicted_score IS NOT NULL ORDER BY a.completed_at DESC LIMIT 50
            """));

        out.put("examAttempts", jdbc.queryForList("""
            SELECT e.kind, COUNT(*) AS attempts, COUNT(DISTINCT a.chat_id) AS learners,
                   AVG(a.score::float / NULLIF(a.total, 0)) AS avg_score
            FROM exam_attempts a JOIN official_exams e ON e.id = a.exam_id
            WHERE a.completed_at > NOW() - INTERVAL '30 days' GROUP BY e.kind ORDER BY e.kind
            """));
        out.put("activity", jdbc.queryForList("""
            SELECT item_type, COUNT(*) AS events, COUNT(DISTINCT chat_id) AS learners,
                   AVG(CASE WHEN is_correct THEN 1.0 WHEN is_correct = FALSE THEN 0.0 END) AS accuracy
            FROM learning_events WHERE created_at > NOW() - INTERVAL '7 days'
            GROUP BY item_type ORDER BY events DESC
            """));
        out.put("taskResponses", jdbc.queryForList("""
            SELECT t.skill, COUNT(*) AS responses, COUNT(DISTINCT r.chat_id) AS learners
            FROM exam_task_responses r JOIN exam_tasks t ON t.id = r.task_set_id GROUP BY t.skill ORDER BY t.skill
            """));
        return out;
    }

    /** Parses the named JSON-text columns so the admin gets objects, not strings. */
    private List<Map<String, Object>> withJson(List<Map<String, Object>> rows, String... columns) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            for (String c : columns) {
                if (m.get(c) instanceof String s) {
                    try {
                        m.put(c, objectMapper.readValue(s, Object.class));
                    } catch (Exception ignored) {}
                }
            }
            out.add(m);
        }
        return out;
    }
}
