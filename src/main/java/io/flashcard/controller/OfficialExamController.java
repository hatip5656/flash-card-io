package io.flashcard.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/official-exams")
public class OfficialExamController {

    private final JdbcTemplate jdbc;

    public OfficialExamController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
    public Map<String, Object> submitAttempt(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String examId,
            @RequestBody Map<String, Object> body) {
        int score = (int) body.getOrDefault("score", 0);
        int total = (int) body.getOrDefault("total", 0);
        String answers = body.containsKey("answers") ? body.get("answers").toString() : "[]";

        jdbc.update(
            "INSERT INTO exam_attempts (chat_id, exam_id, score, total, answers) VALUES (CAST(? AS BIGINT), ?, ?, ?, CAST(? AS JSONB))",
            userId, examId, score, total, answers);

        return Map.of("ok", true, "score", score, "total", total);
    }

    @DeleteMapping("/{examId}")
    public Map<String, Object> deleteExam(@PathVariable String examId) {
        int deleted = jdbc.update("DELETE FROM official_exams WHERE id = ?", examId);
        return Map.of("ok", true, "deleted", deleted);
    }

    @PostMapping("/seed")
    public Map<String, Object> seedExams(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exams = (List<Map<String, Object>>) body.get("exams");
        int inserted = 0;
        int failed = 0;
        for (var exam : exams) {
            try {
                String questions = exam.get("questions") instanceof String
                    ? (String) exam.get("questions")
                    : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(exam.get("questions"));
                jdbc.update(
                    "INSERT INTO official_exams (id, cefr_level, title, title_tr, title_en, source, questions) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB)) ON CONFLICT (id) DO UPDATE SET questions = CAST(EXCLUDED.questions AS JSONB), title = EXCLUDED.title",
                    exam.get("id"), exam.get("level"), exam.get("title"),
                    exam.get("titleTr"), exam.get("titleEn"), exam.get("source"), questions);
                inserted++;
            } catch (Exception e) {
                failed++;
                e.printStackTrace();
            }
        }
        return Map.of("ok", true, "inserted", inserted, "failed", failed);
    }
}
