package io.flashcard.controller;

import io.flashcard.service.LearnerModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/official-exams")
public class OfficialExamController {

    private final JdbcTemplate jdbc;
    private final LearnerModelService learnerModel;

    public OfficialExamController(JdbcTemplate jdbc, LearnerModelService learnerModel) {
        this.jdbc = jdbc;
        this.learnerModel = learnerModel;
    }

    @GetMapping
    public Map<String, Object> listExams(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String level) {
        String sql = "SELECT e.id, e.cefr_level, e.title, e.title_tr, e.title_en, e.source, " +
                     "COALESCE(jsonb_array_length(e.questions), 0) as question_count, " +
                     "(SELECT COUNT(*) FROM exam_attempts a WHERE a.exam_id = e.id AND a.chat_id = CAST(? AS BIGINT)) as attempt_count, " +
                     "(SELECT MAX(a.score) FROM exam_attempts a WHERE a.exam_id = e.id AND a.chat_id = CAST(? AS BIGINT)) as best_score, " +
                     "(SELECT MAX(a.total) FROM exam_attempts a WHERE a.exam_id = e.id AND a.chat_id = CAST(? AS BIGINT)) as best_total " +
                     "FROM official_exams e ";
        List<Object> params = new ArrayList<>(List.of(userId, userId, userId));
        if (level != null && !level.isEmpty()) {
            sql += "WHERE e.cefr_level = ? ";
            params.add(level);
        }
        sql += "ORDER BY e.cefr_level, e.id";
        List<Map<String, Object>> exams = jdbc.queryForList(sql, params.toArray());
        return Map.of("exams", exams);
    }

    @GetMapping("/{examId}")
    public Map<String, Object> getExam(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String examId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, cefr_level, title, title_tr, title_en, source, questions::text as questions FROM official_exams WHERE id = ?",
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
        var result = learnerModel.gradeExam(Long.parseLong(userId), examId, answers, duration);
        if (result == null) return ResponseEntity.status(404).body(Map.of("error", "Exam not found"));
        return ResponseEntity.ok(result);
    }
}
