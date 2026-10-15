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
                jdbc.update(
                    "INSERT INTO official_exams (id, cefr_level, title, title_tr, title_en, source, questions) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB)) ON CONFLICT (id) DO UPDATE SET questions = CAST(EXCLUDED.questions AS JSONB), title = EXCLUDED.title",
                    exam.get("id"), exam.get("level"), exam.get("title"),
                    exam.get("titleTr"), exam.get("titleEn"), exam.get("source"), questions);
                inserted++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to seed exam {}: {}", exam.get("id"), e.getMessage());
            }
        }
        examBank.invalidate();
        return Map.of("ok", true, "inserted", inserted, "failed", failed);
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
