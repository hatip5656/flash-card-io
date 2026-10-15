package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.service.ExamBankService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Exam content management: seeding, deleting, and the per-question tags the learner model uses. */
@RestController
@RequestMapping("/api/admin/official-exams")
public class AdminOfficialExamController {

    private static final Logger log = LoggerFactory.getLogger(AdminOfficialExamController.class);

    private final JdbcTemplate jdbc;
    private final ExamBankService examBank;
    private final ObjectMapper objectMapper;

    public AdminOfficialExamController(JdbcTemplate jdbc, ExamBankService examBank, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.examBank = examBank;
        this.objectMapper = objectMapper;
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
                    : objectMapper.writeValueAsString(exam.get("questions"));
                String scoring = exam.get("scoring") == null ? null : objectMapper.writeValueAsString(exam.get("scoring"));
                jdbc.update(
                    "INSERT INTO official_exams (id, cefr_level, title, title_tr, title_en, source, questions, " +
                    "kind, skill, variant, sort_order, scoring, time_limit_minutes, plan_role) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, CAST(? AS JSONB), ?, ?) " +
                    "ON CONFLICT (id) DO UPDATE SET questions = EXCLUDED.questions, title = EXCLUDED.title, " +
                    "title_tr = COALESCE(EXCLUDED.title_tr, official_exams.title_tr), " +
                    "title_en = COALESCE(EXCLUDED.title_en, official_exams.title_en), " +
                    "kind = EXCLUDED.kind, skill = COALESCE(EXCLUDED.skill, official_exams.skill), " +
                    "variant = EXCLUDED.variant, sort_order = EXCLUDED.sort_order, " +
                    "scoring = COALESCE(EXCLUDED.scoring, official_exams.scoring), " +
                    "time_limit_minutes = COALESCE(EXCLUDED.time_limit_minutes, official_exams.time_limit_minutes), " +
                    "plan_role = COALESCE(EXCLUDED.plan_role, official_exams.plan_role)",
                    exam.get("id"), exam.get("level"), exam.get("title"),
                    exam.get("titleTr"), exam.get("titleEn"), exam.get("source"), questions,
                    exam.get("kind") != null ? exam.get("kind") : "exam", exam.get("skill"),
                    exam.get("variant"), exam.get("sortOrder"), scoring, exam.get("timeLimitMinutes"), exam.get("planRole"));
                inserted++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to seed exam {}: {}", exam.get("id"), e.getMessage());
            }
        }
        examBank.invalidate();
        return Map.of("ok", true, "inserted", inserted, "failed", failed);
    }

    /**
     * Body: {scoring: {...}, timeLimitMinutes: n|null, planRole: 'goal'|'practice'|null}. The grading rules,
     * time limit and role in the learning plan live here, in the database; send only the fields to change.
     */
    @PutMapping("/{examId}/rules")
    public ResponseEntity<?> putRules(@PathVariable String examId, @RequestBody Map<String, Object> body) throws Exception {
        if (examBank.getExam(examId) == null) return ResponseEntity.status(404).body(Map.of("error", "Exam not found"));
        int updated = 0;
        if (body.containsKey("scoring")) {
            Object scoring = body.get("scoring");
            updated += jdbc.update("UPDATE official_exams SET scoring = CAST(? AS JSONB) WHERE id = ?",
                scoring == null ? null : objectMapper.writeValueAsString(scoring), examId);
        }
        if (body.containsKey("timeLimitMinutes")) {
            Object t = body.get("timeLimitMinutes");
            updated += jdbc.update("UPDATE official_exams SET time_limit_minutes = ? WHERE id = ?",
                t instanceof Number n ? n.intValue() : null, examId);
        }
        if (body.containsKey("planRole")) {
            Object role = body.get("planRole");
            if (role != null && !ExamBankService.ROLE_GOAL.equals(role) && !ExamBankService.ROLE_PRACTICE.equals(role)) {
                return ResponseEntity.badRequest().body(Map.of("error", "planRole must be goal, practice or null"));
            }
            updated += jdbc.update("UPDATE official_exams SET plan_role = ? WHERE id = ?", role, examId);
        }
        examBank.invalidate();
        return ResponseEntity.ok(Map.of("ok", true, "updated", updated));
    }

    @DeleteMapping("/{examId}")
    public Map<String, Object> deleteExam(@PathVariable String examId) {
        int deleted = jdbc.update("DELETE FROM official_exams WHERE id = ?", examId);
        examBank.invalidate();
        return Map.of("ok", true, "deleted", deleted);
    }

    @GetMapping("/{examId}/tags")
    public ResponseEntity<?> getTags(@PathVariable String examId) {
        var exam = examBank.getExam(examId);
        if (exam == null) return ResponseEntity.status(404).body(Map.of("error", "Exam not found"));
        var tags = exam.questions().stream().map(q -> {
            var t = exam.tag(q.index());
            return Map.<String, Object>of("questionIndex", q.index(), "prompt", q.prompt(),
                "skill", t.skill() != null ? t.skill() : "", "grammarLessonIds", t.grammarLessonIds(),
                "keyWords", t.keyWords());
        }).toList();
        return ResponseEntity.ok(Map.of("examId", examId, "questionCount", exam.questions().size(), "tags", tags));
    }

    /** Body: {tags: [{questionIndex, skill, grammarLessonIds[], keyWords[]}]}. Upserts by question index. */
    @PutMapping("/{examId}/tags")
    public ResponseEntity<?> putTags(@PathVariable String examId, @RequestBody Map<String, Object> body) throws Exception {
        var exam = examBank.getExam(examId);
        if (exam == null) return ResponseEntity.status(404).body(Map.of("error", "Exam not found"));
        if (!(body.get("tags") instanceof List<?> list)) {
            return ResponseEntity.badRequest().body(Map.of("error", "tags array required"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) list;
        for (var t : tags) {
            if (!(t.get("questionIndex") instanceof Number n) || exam.question(n.intValue()) == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No question at index " + t.get("questionIndex")));
            }
        }
        examBank.saveTags(examId, tags);
        return ResponseEntity.ok(Map.of("ok", true, "saved", tags.size()));
    }
}
