package io.flashcard.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Writing and speaking tasks (exam_tasks). They are not graded: the learner writes or records an answer,
 * then compares it with the model answer and examiner comment. Written answers are kept per learner.
 */
@RestController
@RequestMapping("/api/exam-tasks")
public class ExamTaskController {

    private static final int MAX_RESPONSE_CHARS = 10_000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ExamTaskController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestHeader("X-User-Id") String userId,
                                    @RequestParam(required = false) String level,
                                    @RequestParam(required = false) String skill) {
        String sql = "SELECT t.id, t.kind, t.skill, t.cefr_level, t.variant, t.title, t.title_tr, t.title_en, t.source, " +
                     "t.time_limit_minutes, jsonb_array_length(t.tasks) AS task_count, " +
                     "(SELECT COUNT(DISTINCT r.task_index) FROM exam_task_responses r " +
                     " WHERE r.task_set_id = t.id AND r.chat_id = CAST(? AS BIGINT)) AS answered_count " +
                     "FROM exam_tasks t ";
        List<Object> params = new ArrayList<>(List.of(userId));
        List<String> where = new ArrayList<>();
        if (level != null && !level.isEmpty()) {
            where.add("t.cefr_level = ?");
            params.add(level);
        }
        if (skill != null && !skill.isEmpty()) {
            where.add("t.skill = ?");
            params.add(skill);
        }
        if (!where.isEmpty()) sql += "WHERE " + String.join(" AND ", where) + " ";
        sql += "ORDER BY t.sort_order NULLS LAST, t.id";
        return Map.of("taskSets", jdbc.queryForList(sql, params.toArray()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@RequestHeader("X-User-Id") String userId, @PathVariable String id) throws Exception {
        var rows = jdbc.queryForList(
            "SELECT id, kind, skill, cefr_level, variant, title, title_tr, title_en, source, time_limit_minutes, intro, " +
            "tasks::text AS tasks FROM exam_tasks WHERE id = ?", id);
        if (rows.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Task set not found"));
        Map<String, Object> set = new LinkedHashMap<>(rows.get(0));
        set.put("tasks", objectMapper.readValue((String) set.get("tasks"), new TypeReference<List<Object>>() {}));
        // The learner's latest answer per task
        Map<String, Object> responses = new LinkedHashMap<>();
        for (var r : jdbc.queryForList("""
                SELECT DISTINCT ON (task_index) task_index, response, created_at FROM exam_task_responses
                WHERE chat_id = CAST(? AS BIGINT) AND task_set_id = ? ORDER BY task_index, created_at DESC
                """, userId, id)) {
            responses.put(String.valueOf(r.get("task_index")),
                Map.of("response", r.get("response"), "createdAt", String.valueOf(r.get("created_at"))));
        }
        set.put("responses", responses);
        return ResponseEntity.ok(set);
    }

    /** Body: {taskIndex, response}. */
    @PostMapping("/{id}/responses")
    public ResponseEntity<?> respond(@RequestHeader("X-User-Id") String userId, @PathVariable String id,
                                     @RequestBody Map<String, Object> body) {
        Integer count = jdbc.query("SELECT jsonb_array_length(tasks) FROM exam_tasks WHERE id = ?",
            rs -> rs.next() ? rs.getInt(1) : null, id);
        if (count == null) return ResponseEntity.status(404).body(Map.of("error", "Task set not found"));
        if (!(body.get("taskIndex") instanceof Number n) || n.intValue() < 0 || n.intValue() >= count) {
            return ResponseEntity.badRequest().body(Map.of("error", "taskIndex out of range"));
        }
        String response = body.get("response") instanceof String s ? s.strip() : "";
        if (response.isEmpty() || response.length() > MAX_RESPONSE_CHARS) {
            return ResponseEntity.badRequest().body(Map.of("error", "response must be 1-" + MAX_RESPONSE_CHARS + " characters"));
        }
        jdbc.update("INSERT INTO exam_task_responses (chat_id, task_set_id, task_index, response) VALUES (CAST(? AS BIGINT), ?, ?, ?)",
            userId, id, n.intValue(), response);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
