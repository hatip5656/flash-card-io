package io.flashcard.controller;

import io.flashcard.service.LearnerModelService;
import io.flashcard.service.LearningPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/official-exams")
public class OfficialExamController {

    private final JdbcTemplate jdbc;
    private final LearnerModelService learnerModel;
    private final LearningPlanService planService;

    public OfficialExamController(JdbcTemplate jdbc, LearnerModelService learnerModel, LearningPlanService planService) {
        this.jdbc = jdbc;
        this.learnerModel = learnerModel;
        this.planService = planService;
    }

    /**
     * kind: 'exam' by default, so app versions that only know reading papers keep seeing just those;
     * 'screening', 'diagnostic', 'sample', or 'all'.
     */
    @GetMapping
    public Map<String, Object> listExams(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false, defaultValue = "exam") String kind) {
        String sql = "SELECT e.id, e.cefr_level, e.title, e.title_tr, e.title_en, e.source, " +
                     "e.kind, e.skill, e.variant, e.time_limit_minutes, " +
                     "COALESCE(jsonb_array_length(e.questions), 0) as question_count, " +
                     "(SELECT COUNT(*) FROM exam_attempts a WHERE a.exam_id = e.id AND a.chat_id = CAST(? AS BIGINT)) as attempt_count, " +
                     "(SELECT MAX(a.score) FROM exam_attempts a WHERE a.exam_id = e.id AND a.chat_id = CAST(? AS BIGINT)) as best_score, " +
                     "(SELECT MAX(a.total) FROM exam_attempts a WHERE a.exam_id = e.id AND a.chat_id = CAST(? AS BIGINT)) as best_total " +
                     "FROM official_exams e ";
        List<Object> params = new ArrayList<>(List.of(userId, userId, userId));
        // Drafts (generated tests awaiting review) are never shown to learners
        List<String> where = new ArrayList<>(List.of("e.status = 'published'"));
        if (level != null && !level.isEmpty()) {
            where.add("e.cefr_level = ?");
            params.add(level);
        }
        if (!"all".equals(kind)) {
            where.add("e.kind = ?");
            params.add(kind);
        }
        sql += "WHERE " + String.join(" AND ", where) + " ";
        sql += "ORDER BY e.kind, e.sort_order NULLS LAST, e.cefr_level, e.id";
        List<Map<String, Object>> exams = jdbc.queryForList(sql, params.toArray());
        return Map.of("exams", exams);
    }

    @GetMapping("/{examId}")
    public Map<String, Object> getExam(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String examId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, cefr_level, title, title_tr, title_en, source, kind, skill, variant, time_limit_minutes, " +
            "scoring::text as scoring, " +
            "questions::text as questions FROM official_exams WHERE id = ? AND status = 'published'",
            examId);
        if (rows.isEmpty()) return Map.of("error", "Exam not found");
        return rows.get(0);
    }

    @PostMapping("/{examId}/submit")
    public ResponseEntity<?> submitAttempt(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String examId,
            @RequestBody Map<String, Object> body) {
        // Graded here: the client's own score/total, if sent, are ignored.
        List<?> answers = body.get("answers") instanceof List<?> l ? l : List.of();
        Integer duration = body.get("durationSeconds") instanceof Number n ? n.intValue() : null;
        long chatId = Long.parseLong(userId);
        Integer published = jdbc.queryForObject(
            "SELECT COUNT(*) FROM official_exams WHERE id = ? AND status = 'published'", Integer.class, examId);
        if (published == null || published == 0) return ResponseEntity.status(404).body(Map.of("error", "Exam not found"));
        // The skill estimate before this attempt counts toward it, so take it before grading
        Double predicted = planService.predictedScore(chatId, examId);
        var result = learnerModel.gradeExam(chatId, examId, answers, duration, predicted);
        if (result == null) return ResponseEntity.status(404).body(Map.of("error", "Exam not found"));
        return ResponseEntity.ok(result);
    }
}
