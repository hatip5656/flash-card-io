package io.flashcard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Official exams with their questions normalised the same way the app normalises them
 * (OfficialExamScreen.startExam), so a question index means the same thing on both sides,
 * plus the per-question tags that say what each question tests.
 */
@Service
public class ExamBankService {

    private static final Logger log = LoggerFactory.getLogger(ExamBankService.class);

    public record Question(int index, String section, String context, String prompt, List<String> options,
                           int correctIndex, String explanation) {}

    public record Tag(String skill, List<String> grammarLessonIds, List<String> keyWords) {
        static final Tag NONE = new Tag(null, List.of(), List.of());
    }

    public record Exam(String id, String cefrLevel, String title, String source, List<Question> questions,
                       Map<Integer, Tag> tags) {
        public Tag tag(int index) {
            return tags.getOrDefault(index, Tag.NONE);
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Map<String, Exam> cache = new ConcurrentHashMap<>();
    private volatile List<Map<String, Object>> index;

    public ExamBankService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void invalidate() {
        cache.clear();
        index = null;
    }

    /** id, cefr_level, source for every exam, ordered by level then id. */
    public List<Map<String, Object>> listExams() {
        var current = index;
        if (current == null) {
            current = jdbc.queryForList("SELECT id, cefr_level, source FROM official_exams ORDER BY cefr_level, id");
            index = current;
        }
        return current;
    }

    public Exam getExam(String examId) {
        Exam cached = cache.get(examId);
        if (cached != null) return cached;
        var rows = jdbc.queryForList(
            "SELECT id, cefr_level, title, source, questions::text AS questions FROM official_exams WHERE id = ?", examId);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        Exam exam = new Exam(examId, (String) row.get("cefr_level"), (String) row.get("title"),
            (String) row.get("source"), parseQuestions((String) row.get("questions")), loadTags(examId));
        cache.put(examId, exam);
        return exam;
    }

    /**
     * The exam per level kept out of the feed so it stays an honest mock test: the first official
     * Harno paper at that level, or the first exam if there is none.
     */
    public String mockExamId(String level) {
        String fallback = null;
        for (var e : listExams()) {
            if (!level.equals(e.get("cefr_level"))) continue;
            String id = (String) e.get("id");
            if (fallback == null) fallback = id;
            Object source = e.get("source");
            if (source != null && source.toString().toLowerCase().contains("harno")) return id;
        }
        return fallback;
    }

    /** Exams at the level that the feed may draw questions from (everything but the mock). */
    public List<String> practiceExamIds(String level) {
        String mock = mockExamId(level);
        return listExams().stream()
            .filter(e -> level.equals(e.get("cefr_level")))
            .map(e -> (String) e.get("id"))
            .filter(id -> !id.equals(mock))
            .toList();
    }

    public Set<String> examLevels() {
        Set<String> levels = new LinkedHashSet<>();
        for (var e : listExams()) levels.add((String) e.get("cefr_level"));
        return levels;
    }

    public void saveTags(String examId, List<Map<String, Object>> tags) throws Exception {
        for (var t : tags) {
            int qi = ((Number) t.get("questionIndex")).intValue();
            jdbc.update("""
                INSERT INTO exam_question_tags (exam_id, question_index, skill, grammar_lesson_ids, key_words)
                VALUES (?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB))
                ON CONFLICT (exam_id, question_index) DO UPDATE SET skill = EXCLUDED.skill,
                  grammar_lesson_ids = EXCLUDED.grammar_lesson_ids, key_words = EXCLUDED.key_words
                """,
                examId, qi, t.get("skill"),
                objectMapper.writeValueAsString(t.getOrDefault("grammarLessonIds", List.of())),
                objectMapper.writeValueAsString(t.getOrDefault("keyWords", List.of())));
        }
        cache.remove(examId);
    }

    private Map<Integer, Tag> loadTags(String examId) {
        Map<Integer, Tag> tags = new HashMap<>();
        try {
            for (var row : jdbc.queryForList(
                    "SELECT question_index, skill, grammar_lesson_ids::text AS g, key_words::text AS k " +
                    "FROM exam_question_tags WHERE exam_id = ?", examId)) {
                tags.put(((Number) row.get("question_index")).intValue(), new Tag(
                    (String) row.get("skill"),
                    objectMapper.readValue((String) row.get("g"), new TypeReference<List<String>>() {}),
                    objectMapper.readValue((String) row.get("k"), new TypeReference<List<String>>() {})));
            }
        } catch (Exception e) {
            log.warn("Could not load tags for exam {}: {}", examId, e.getMessage());
        }
        return tags;
    }

    @SuppressWarnings("unchecked")
    List<Question> parseQuestions(String json) {
        List<Map<String, Object>> raw;
        try {
            raw = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Unparseable exam questions: {}", e.getMessage());
            return List.of();
        }
        List<Question> questions = new ArrayList<>();
        for (var q : raw) {
            List<String> options = new ArrayList<>();
            if (q.get("options") instanceof List<?> opts) {
                for (Object o : opts) {
                    if (o instanceof Map<?, ?> m && m.get("text") != null) options.add(m.get("label") + ". " + m.get("text"));
                    else options.add(String.valueOf(o));
                }
            }
            int ci = correctIndex(q, options);
            String prompt = firstNonBlank(q.get("prompt"), q.get("question"), q.get("questionTr"));
            // Same filter as the app; it runs after mapping, so indices follow the filtered list.
            if (options.size() < 2 || prompt.isEmpty()) continue;
            questions.add(new Question(questions.size(),
                firstNonBlank(q.get("section")),
                emptyToNull(firstNonBlank(q.get("context"), q.get("taskInstruction"))),
                prompt, options, ci,
                firstNonBlank(q.get("explanation"), q.get("explanationTr"))));
        }
        return questions;
    }

    private static int correctIndex(Map<String, Object> q, List<String> options) {
        int ci;
        if (q.get("correctIndex") instanceof Number n) {
            ci = n.intValue();
        } else if (q.get("answer") instanceof String ans && !ans.isEmpty() && ans.length() <= 2) {
            ci = -1;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).startsWith(ans + ".") || options.get(i).startsWith(ans + " ")) { ci = i; break; }
            }
            if (ci < 0) ci = ans.charAt(0) - 'A';
        } else {
            ci = 0;
        }
        return ci < 0 || ci >= options.size() ? 0 : ci;
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v != null && !v.toString().isEmpty()) return v.toString();
        }
        return "";
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
