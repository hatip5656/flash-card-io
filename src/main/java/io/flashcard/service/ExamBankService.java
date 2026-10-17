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

    /**
     * One scorable item. A multiple-choice item is right when the chosen index equals correctIndex; a
     * text_gap item (typed answer) is right when the normalised answer is one of {@code accepted}.
     */
    public record Question(int index, String section, String context, String prompt, List<String> options,
                           int correctIndex, String explanation, String type, List<String> accepted,
                           List<String> audio, List<Object> images) {

        public Question(int index, String section, String context, String prompt, List<String> options,
                        int correctIndex, String explanation) {
            this(index, section, context, prompt, options, correctIndex, explanation, TYPE_CHOICE, List.of());
        }

        public Question(int index, String section, String context, String prompt, List<String> options,
                        int correctIndex, String explanation, String type, List<String> accepted) {
            this(index, section, context, prompt, options, correctIndex, explanation, type, accepted, List.of(), List.of());
        }

        public boolean isTextGap() {
            return TYPE_TEXT_GAP.equals(type);
        }

        /** The answer as the learner model stores it: the option index, or the typed text. */
        public String correctAnswer() {
            return isTextGap() ? String.join(" / ", accepted) : String.valueOf(correctIndex);
        }

        public boolean isCorrect(Object answer) {
            if (answer == null) return false;
            if (isTextGap()) {
                String given = normalise(answer.toString());
                return !given.isEmpty() && accepted.stream().anyMatch(a -> normalise(a).equals(given));
            }
            if (answer instanceof Number n) return n.intValue() == correctIndex;
            try {
                return Integer.parseInt(answer.toString().trim()) == correctIndex;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static final String TYPE_CHOICE = "multiple_choice";
    public static final String TYPE_TEXT_GAP = "text_gap";

    /** Case, surrounding punctuation and runs of spaces do not count against a typed answer. */
    static String normalise(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT)
            .replace('\u2019', '\'').replace('\u2018', '\'')
            .replaceAll("\\s+", " ");
        return t.replaceAll("^[\\p{Punct}\u201e\u201c\u201d\\s]+|[\\p{Punct}\u201e\u201c\u201d\\s]+$", "");
    }

    public record Tag(String skill, List<String> grammarLessonIds, List<String> keyWords) {
        static final Tag NONE = new Tag(null, List.of(), List.of());
    }

    public record Exam(String id, String cefrLevel, String title, String source, List<Question> questions,
                       Map<Integer, Tag> tags, String kind, String skill, Map<String, Object> scoring) {

        public Exam(String id, String cefrLevel, String title, String source, List<Question> questions,
                    Map<Integer, Tag> tags) {
            this(id, cefrLevel, title, source, questions, tags, KIND_EXAM, null, Map.of());
        }

        public Tag tag(int index) {
            return tags.getOrDefault(index, Tag.NONE);
        }

        /** The exam's first task: what a learner below the exam's level can already attempt. */
        public boolean isFirstTask(Question q) {
            return !questions.isEmpty() && Objects.equals(questions.get(0).section(), q.section());
        }

        /** The question's tagged sub-skill, or the exam's own skill (reading, listening, …). */
        public String skillOf(Question q) {
            String tagged = tag(q.index()).skill();
            return tagged != null ? tagged : skill;
        }

        /** The question at a stored position, or null (out of range, or dropped as unrenderable). */
        public Question question(int index) {
            for (Question q : questions) {
                if (q.index() == index) return q;
            }
            return null;
        }
    }

    /** The reading papers the learning plan and readiness draw on; other kinds stand alone. */
    public static final String KIND_EXAM = "exam";

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

    public static final String ROLE_GOAL = "goal";
    public static final String ROLE_PRACTICE = "practice";

    /** id, cefr_level, source, skill, plan_role for every exam the learning plan uses (plan_role set). */
    public List<Map<String, Object>> listExams() {
        var current = index;
        if (current == null) {
            current = jdbc.queryForList(
                "SELECT id, cefr_level, source, skill, plan_role FROM official_exams " +
                "WHERE plan_role IS NOT NULL AND status = 'published' " +
                "ORDER BY cefr_level, sort_order NULLS LAST, id");
            index = current;
        }
        return current;
    }

    private List<Map<String, Object>> withRole(String level, String role) {
        return listExams().stream()
            .filter(e -> level.equals(e.get("cefr_level")) && role.equals(e.get("plan_role")))
            .toList();
    }

    /** The goal exams at a level, one per skill (skill → exam id, first in stored order wins). */
    public Map<String, String> goalExams(String level) {
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : withRole(level, ROLE_GOAL)) {
            String skill = e.get("skill") != null ? (String) e.get("skill") : "reading";
            out.putIfAbsent(skill, (String) e.get("id"));
        }
        return out;
    }

    public Exam getExam(String examId) {
        Exam cached = cache.get(examId);
        if (cached != null) return cached;
        var rows = jdbc.queryForList(
            "SELECT id, cefr_level, title, source, kind, skill, scoring::text AS scoring, questions::text AS questions " +
            "FROM official_exams WHERE id = ?", examId);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        Exam exam = new Exam(examId, (String) row.get("cefr_level"), (String) row.get("title"),
            (String) row.get("source"), parseQuestions((String) row.get("questions")), loadTags(examId),
            row.get("kind") != null ? (String) row.get("kind") : KIND_EXAM, (String) row.get("skill"), parseScoring((String) row.get("scoring")));
        cache.put(examId, exam);
        return exam;
    }

    /** Exams at the level that the feed may draw questions from; goal exams never appear in the feed. */
    public List<String> practiceExamIds(String level) {
        return withRole(level, ROLE_PRACTICE).stream().map(e -> (String) e.get("id")).toList();
    }

    /** Every exam at the level the plan uses (goal and practice): the evidence for readiness. */
    public Set<String> planExamIds(String level) {
        Set<String> ids = new LinkedHashSet<>();
        for (var e : listExams()) if (level.equals(e.get("cefr_level"))) ids.add((String) e.get("id"));
        return ids;
    }

    /** The exam's own pass mark (0..1) from its stored rules, or null when it has none. */
    public Double passMark(String examId) {
        var exam = examId != null ? getExam(examId) : null;
        return exam != null && exam.scoring().get("passMark") instanceof Number n ? n.doubleValue() : null;
    }

    /** Levels a learner can aim for: those with goal exams (or, failing that, any plan exams). */
    public Set<String> examLevels() {
        Set<String> goal = new LinkedHashSet<>(), any = new LinkedHashSet<>();
        for (var e : listExams()) {
            any.add((String) e.get("cefr_level"));
            if (ROLE_GOAL.equals(e.get("plan_role"))) goal.add((String) e.get("cefr_level"));
        }
        return goal.isEmpty() ? any : goal;
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

    private Map<String, Object> parseScoring(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Unparseable exam scoring: {}", e.getMessage());
            return Map.of();
        }
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
        String sectionContext = null, contextSection = null;
        for (int i = 0; i < raw.size(); i++) {
            var q = raw.get(i);
            List<String> options = new ArrayList<>();
            if (q.get("options") instanceof List<?> opts) {
                for (Object o : opts) {
                    if (o instanceof Map<?, ?> m && m.get("text") != null) options.add(m.get("label") + ". " + m.get("text"));
                    else options.add(String.valueOf(o));
                }
            }
            String prompt = firstNonBlank(q.get("prompt"), q.get("question"), q.get("questionTr"));
            // A task's shared text (the ads of a matching task) is often stored on its first question
            // only. The feed shows questions one at a time, so later questions of the section inherit it.
            String section = firstNonBlank(q.get("section"));
            String context = emptyToNull(firstNonBlank(q.get("context"), q.get("taskInstruction")));
            if (context != null) { sectionContext = context; contextSection = section; }
            else if (!section.isEmpty() && section.equals(contextSection)) context = sectionContext;
            // Bare letters ("A".."F") point into that text; show what each letter stands for
            List<String> shown = options;
            // ...unless the letters label pictures, which the card shows
            boolean lettersArePictures = q.get("images") instanceof List<?> imgs && imgs.stream()
                .anyMatch(im -> im instanceof Map<?, ?> m && options.contains(String.valueOf(m.get("label"))));
            if (context != null && !lettersArePictures) {
                var gapMatch = GAP.matcher(prompt);
                String gap = q.get("gap") != null ? String.valueOf(q.get("gap")) : gapMatch.find() ? gapMatch.group(1) : null;
                var expanded = expandLetterOptions(options, context, gap);
                if (expanded != null) shown = expanded;
            }
            boolean textGap = TYPE_TEXT_GAP.equals(q.get("type"));
            List<String> accepted = new ArrayList<>();
            if (textGap && q.get("accepted") instanceof List<?> acc) {
                for (Object a : acc) if (a != null && !a.toString().isBlank()) accepted.add(a.toString());
            }
            // Same filter as the app. A question keeps its position in the stored array, which is
            // what the app sends answers by and what the tags are keyed on.
            if (prompt.isEmpty()) continue;
            if (textGap ? accepted.isEmpty() : options.size() < 2) continue;
            questions.add(new Question(i, section, context,
                // The answer key refers to the stored letters, so grade against those
                prompt, shown, textGap ? -1 : correctIndex(q, options),
                firstNonBlank(q.get("explanation"), q.get("explanationTr")),
                textGap ? TYPE_TEXT_GAP : TYPE_CHOICE, List.copyOf(accepted),
                q.get("audio") instanceof List<?> au ? au.stream().filter(String.class::isInstance).map(String.class::cast).toList() : List.of(),
                q.get("images") instanceof List<?> im ? List.copyOf(im) : List.of()));
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

    private static final java.util.regex.Pattern LETTER_LINE =
        java.util.regex.Pattern.compile("^\\s*([A-Z])(?:\\s*[–—:.)-])?\\s+(\\S.*?)\\s*$");
    /** Longer texts (a whole ad or article) make poor answer buttons; those keep their letters. */
    private static final int MAX_OPTION_CHARS = 200;
    private static final java.util.regex.Pattern GAP = java.util.regex.Pattern.compile("__\\((\\d+)\\)__");

    /**
     * Options that are single letters, each defined in the context, become those texts. Returns null,
     * so the card keeps the letters and the text, whenever the definitions are not found or ambiguous.
     * <ul>
     *   <li>When the context holds this question's gap ("7. ... __(7)__") with its own options under
     *       it, only that block is read: several questions often share one text, each with A/B/C.</li>
     *   <li>Otherwise a letter is defined once in the whole text, on its own "A – text" line or inline
     *       as "... A Text one. B Text two." (how the TESTEST scrape flattened its tables).</li>
     * </ul>
     */
    static List<String> expandLetterOptions(List<String> options, String context, String gap) {
        if (options.isEmpty() || !options.stream().allMatch(o -> o.trim().matches("[A-Z]"))) return null;
        if (gap != null) {
            String marker = "__(" + gap + ")__";
            int at = context.indexOf(marker);
            if (at >= 0) {
                int from = at + marker.length();
                var next = GAP.matcher(context);
                int end = next.find(from) ? next.start() : context.length();
                var own = fromLines(options, context.substring(from, end));
                if (own != null) return own;
            }
        }
        var lines = fromLines(options, context);
        return lines != null ? lines : inlineLetterTexts(options, context);
    }

    private static List<String> fromLines(List<String> options, String text) {
        Map<String, List<String>> byLetter = new HashMap<>();
        for (String line : text.split("\\R")) {
            var m = LETTER_LINE.matcher(line);
            if (m.matches()) byLetter.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(m.group(2));
        }
        List<String> out = new ArrayList<>();
        for (String o : options) {
            var texts = byLetter.get(o.trim());
            if (texts == null || texts.size() != 1 || texts.get(0).length() > MAX_OPTION_CHARS) return null;
            out.add(texts.get(0));
        }
        return out;
    }

    private static List<String> inlineLetterTexts(List<String> options, String context) {
        List<String> found = null;
        for (String paragraph : context.split("\\R\\s*\\R")) {
            // Each letter in turn, standing alone and followed by a capitalised word
            List<int[]> spans = new ArrayList<>();
            int from = 0;
            for (String o : options) {
                var m = java.util.regex.Pattern.compile("(?:^|\\s)" + o.trim() + "\\s+(?=\\p{Lu})").matcher(paragraph);
                if (!m.find(from)) { spans = null; break; }
                spans.add(new int[]{m.start(), m.end()});
                from = m.end();
            }
            if (spans == null) continue;
            List<String> out = new ArrayList<>();
            for (int i = 0; i < spans.size(); i++) {
                int end = i + 1 < spans.size() ? spans.get(i + 1)[0] : paragraph.length();
                String text = paragraph.substring(spans.get(i)[1], end).trim();
                if (text.isEmpty() || text.length() > MAX_OPTION_CHARS) { out = null; break; }
                out.add(text);
            }
            if (out == null) continue;
            if (found != null) return null; // defined in two places: cannot tell which belongs here
            found = out;
        }
        return found;
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
