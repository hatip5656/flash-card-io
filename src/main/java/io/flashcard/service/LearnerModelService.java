package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.model.GrammarLesson;
import io.flashcard.model.Word;
import io.flashcard.repository.LearningRepository;
import io.flashcard.repository.LearningRepository.Event;
import io.flashcard.repository.SentWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns learner interactions into the learner model: an event log, a mastery estimate per grammar
 * lesson, and (for exam misses) the key words pushed back into spaced repetition.
 */
@Service
public class LearnerModelService {

    private static final Logger log = LoggerFactory.getLogger(LearnerModelService.class);

    static final Set<String> ITEM_TYPES = Set.of(
        "word_quiz", "practice_question", "exam_question", "game_word", "grammar_lesson", "lesson_media", "word_card");

    private final LearningRepository learningRepo;
    private final SentWordRepository sentWordRepo;
    private final ExamBankService examBank;
    private final GrammarBankService grammarBank;
    private final WordBankService wordBank;
    private final ObjectMapper objectMapper;

    public LearnerModelService(LearningRepository learningRepo, SentWordRepository sentWordRepo,
                               ExamBankService examBank, GrammarBankService grammarBank,
                               WordBankService wordBank, ObjectMapper objectMapper) {
        this.learningRepo = learningRepo;
        this.sentWordRepo = sentWordRepo;
        this.examBank = examBank;
        this.grammarBank = grammarBank;
        this.wordBank = wordBank;
        this.objectMapper = objectMapper;
    }

    public record ExamItemResult(int index, boolean isCorrect, int correctIndex) {}

    /**
     * Records a batch of app events. Exam questions are graded here, whatever the client claims;
     * returns the grading for those, in batch order.
     */
    public List<Map<String, Object>> recordEvents(long chatId, List<Map<String, Object>> raw) {
        Set<String> lessonIds = lessonIds();
        List<Event> events = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> e = raw.get(i);
            String itemType = str(e.get("itemType"));
            if (itemType == null || !ITEM_TYPES.contains(itemType)) continue;
            String source = str(e.get("source"));
            String itemId = str(e.get("itemId"));
            Integer latency = e.get("latencyMs") instanceof Number n ? Math.max(0, Math.min(n.intValue(), 3_600_000)) : null;
            String meta = toJson(e.get("meta"));

            if ("exam_question".equals(itemType)) {
                var graded = gradeExamItem(chatId, itemId, str(e.get("userAnswer")), source != null ? source : "feed", latency, meta);
                if (graded == null) continue;
                events.add(graded.event());
                results.add(Map.of("index", i, "isCorrect", graded.isCorrect(), "correctIndex", graded.correctIndex()));
                continue;
            }

            String lessonId = str(e.get("grammarLessonId"));
            if (lessonId != null && !lessonIds.contains(lessonId)) lessonId = null;
            Boolean isCorrect = e.get("isCorrect") instanceof Boolean b ? b : null;

            events.add(new Event(itemType, source, itemId, str(e.get("wordId")), str(e.get("estonian")), lessonId,
                lessonId != null ? "grammar" : null, str(e.get("questionType")), isCorrect,
                str(e.get("userAnswer")), str(e.get("correctAnswer")), latency, meta));

            if ("practice_question".equals(itemType) && lessonId != null && isCorrect != null) {
                updateMastery(chatId, lessonId, isCorrect, false);
            }
        }
        learningRepo.insertEvents(chatId, events);
        return results;
    }

    private record GradedItem(Event event, boolean isCorrect, int correctIndex) {}

    private GradedItem gradeExamItem(long chatId, String itemId, String userAnswer, String source,
                                     Integer latency, String meta) {
        if (itemId == null || !itemId.contains("#")) return null;
        String examId = itemId.substring(0, itemId.indexOf('#'));
        int qi;
        try {
            qi = Integer.parseInt(itemId.substring(itemId.indexOf('#') + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
        var exam = examBank.getExam(examId);
        var q = exam != null ? exam.question(qi) : null;
        if (q == null) return null;
        Integer chosen = null;
        try {
            if (userAnswer != null && !userAnswer.isBlank()) chosen = Integer.parseInt(userAnswer.trim());
        } catch (NumberFormatException ignored) {}
        boolean correct = chosen != null && chosen == q.correctIndex();
        var tag = exam.tag(qi);
        if (chosen != null) applyExamOutcome(chatId, tag, correct);
        return new GradedItem(new Event("exam_question", source, examId + "#" + qi, null, null,
            tag.grammarLessonIds().isEmpty() ? null : tag.grammarLessonIds().get(0), tag.skill(), "multiple_choice",
            correct, chosen != null ? chosen.toString() : null, String.valueOf(q.correctIndex()), latency, meta),
            correct, q.correctIndex());
    }

    /** A graded exam question moves grammar mastery for its rules; a miss sends its key words back to review. */
    private void applyExamOutcome(long chatId, ExamBankService.Tag tag, boolean correct) {
        Set<String> lessonIds = lessonIds();
        for (String lessonId : tag.grammarLessonIds()) {
            if (lessonIds.contains(lessonId)) updateMastery(chatId, lessonId, correct, true);
        }
        if (!correct) reviewKeyWords(chatId, tag.keyWords());
    }

    private void reviewKeyWords(long chatId, List<String> keyWords) {
        if (keyWords.isEmpty()) return;
        Map<String, Word> byValue = wordsByValue();
        for (String kw : keyWords) {
            Word w = byValue.get(kw.toLowerCase(Locale.ROOT));
            if (w == null) continue;
            sentWordRepo.markWordSent(chatId, w.getId(), w.getEstonian(), w.getEnglish());
            // Quality 1 = failed recall: due again tomorrow, ease lowered.
            sentWordRepo.updateSm2(chatId, w.getEstonian(), 1);
        }
    }

    /**
     * Mastery is a running mean that starts at 0.5 and weights recent answers more once there is
     * enough evidence (alpha floors at 0.2, i.e. roughly the last 5 answers dominate).
     */
    void updateMastery(long chatId, String lessonId, boolean correct, boolean fromExam) {
        var row = learningRepo.getMastery(chatId, lessonId);
        int attempts = row != null ? ((Number) row.get("attempts")).intValue() : 0;
        int right = row != null ? ((Number) row.get("correct")).intValue() : 0;
        int examAttempts = row != null ? ((Number) row.get("exam_attempts")).intValue() : 0;
        int examMisses = row != null ? ((Number) row.get("exam_misses")).intValue() : 0;
        double mastery = row != null ? ((Number) row.get("mastery")).doubleValue() : 0.5;

        attempts++;
        if (correct) right++;
        if (fromExam) {
            examAttempts++;
            if (!correct) examMisses++;
        }
        mastery = nextMastery(mastery, attempts, correct);
        learningRepo.saveMastery(chatId, lessonId, attempts, right, examAttempts, examMisses, mastery,
            recheckDays(mastery));
    }

    static double nextMastery(double mastery, int attemptsIncludingThis, boolean correct) {
        double alpha = Math.max(0.2, 1.0 / (attemptsIncludingThis + 1));
        return mastery + alpha * ((correct ? 1.0 : 0.0) - mastery);
    }

    static int recheckDays(double mastery) {
        if (mastery >= 0.8) return 7;
        if (mastery >= 0.6) return 3;
        return 1;
    }

    // ── Full exam ──

    /** Grades a whole exam attempt on the server and feeds every question into the learner model. */
    public Map<String, Object> gradeExam(long chatId, String examId, List<?> answers, Integer durationSeconds) {
        var exam = examBank.getExam(examId);
        if (exam == null) return null;

        int score = 0;
        Map<String, int[]> sections = new LinkedHashMap<>();
        Map<String, int[]> skills = new LinkedHashMap<>();
        Map<String, int[]> lessons = new LinkedHashMap<>();
        List<Map<String, Object>> perQuestion = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        List<Object> stored = new ArrayList<>();

        for (var q : exam.questions()) {
            Object a = q.index() < answers.size() ? answers.get(q.index()) : null;
            // An option index for multiple choice, the typed text for a text gap; anything else is a skip.
            Object chosen = q.isTextGap()
                ? (a instanceof String t && !t.isBlank() ? t.strip() : null)
                : (a instanceof Number n ? (Object) n.intValue() : null);
            if (chosen instanceof String t && t.length() > 200) chosen = t.substring(0, 200);
            stored.add(chosen);
            boolean correct = q.isCorrect(chosen);
            if (correct) score++;
            var tag = exam.tag(q.index());

            tally(sections, sectionName(q.section()), correct);
            // Tagged exams break down by question skill; the others by the exam's own (listening, writing…).
            String skill = tag.skill() != null ? tag.skill() : exam.skill();
            if (skill != null) tally(skills, skill, correct);
            for (String l : tag.grammarLessonIds()) tally(lessons, l, correct);
            Map<String, Object> pq = new LinkedHashMap<>();
            pq.put("index", q.index());
            pq.put("isCorrect", correct);
            pq.put("correctIndex", q.correctIndex());
            if (q.isTextGap()) pq.put("accepted", q.accepted());
            perQuestion.add(pq);

            events.add(new Event("exam_question", "exam", examId + "#" + q.index(), null, null,
                tag.grammarLessonIds().isEmpty() ? null : tag.grammarLessonIds().get(0),
                tag.skill() != null ? tag.skill() : exam.skill(),
                q.type(), correct, chosen != null ? chosen.toString() : null,
                q.correctAnswer(), null, null));
            // Skipped questions count as wrong for the score but say nothing about a rule or word.
            if (chosen != null) applyExamOutcome(chatId, tag, correct);
        }

        int total = exam.questions().size();
        var sectionList = sections.entrySet().stream()
            .map(s -> Map.<String, Object>of("name", s.getKey(), "score", s.getValue()[0], "total", s.getValue()[1]))
            .toList();
        var skillList = skills.entrySet().stream()
            .map(s -> Map.<String, Object>of("skill", s.getKey(), "correct", s.getValue()[0], "total", s.getValue()[1]))
            .toList();
        Map<String, GrammarLesson> lessonById = grammarBank.getAllLessons().stream()
            .collect(Collectors.toMap(GrammarLesson::id, l -> l, (x, y) -> x));
        var weakLessons = lessons.entrySet().stream()
            .filter(l -> l.getValue()[0] < l.getValue()[1] && lessonById.containsKey(l.getKey()))
            .sorted((x, y) -> Integer.compare(y.getValue()[1] - y.getValue()[0], x.getValue()[1] - x.getValue()[0]))
            .map(l -> {
                var lesson = lessonById.get(l.getKey());
                return Map.<String, Object>of("lessonId", lesson.id(), "topic", lesson.topic(),
                    "topicTr", lesson.topicTr() != null ? lesson.topicTr() : lesson.topic(),
                    "missed", l.getValue()[1] - l.getValue()[0], "total", l.getValue()[1]);
            })
            .toList();

        learningRepo.insertEvents(chatId, events);
        learningRepo.saveExamAttempt(chatId, examId, score, total, toJson(stored), toJson(sectionList), durationSeconds);

        double pct = total == 0 ? 0 : (double) score / total;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("score", score);
        result.put("total", total);
        result.put("percentage", Math.round(pct * 100));
        // Pass/fail only for exams whose stored rules have a pass mark
        Double passMark = exam.scoring().get("passMark") instanceof Number n ? n.doubleValue() : null;
        result.put("passMark", passMark);
        result.put("passed", passMark != null ? pct >= passMark : null);
        result.put("sections", sectionList);
        result.put("skills", skillList);
        result.put("weakLessons", weakLessons);
        result.put("perQuestion", perQuestion);
        result.put("kind", exam.kind());
        var verdict = ExamScoring.verdict(exam.scoring(), sections, score, total);
        if (verdict != null) result.put("verdict", verdict);
        return result;
    }

    /** "Esimene ülesanne – Situatsioonid ja kuulutused" → "Esimene ülesanne". */
    static String sectionName(String section) {
        if (section == null || section.isBlank()) return "—";
        int dash = section.indexOf(" – ");
        return dash > 0 ? section.substring(0, dash).trim() : section.trim();
    }

    private static void tally(Map<String, int[]> map, String key, boolean correct) {
        int[] c = map.computeIfAbsent(key, k -> new int[2]);
        if (correct) c[0]++;
        c[1]++;
    }

    private Set<String> lessonIds() {
        return grammarBank.getAllLessons().stream().map(GrammarLesson::id).collect(Collectors.toSet());
    }

    private Map<String, Word> wordsByValue() {
        Map<String, Word> map = new HashMap<>();
        for (Word w : wordBank.getAllWords()) map.putIfAbsent(w.getEstonian().toLowerCase(Locale.ROOT), w);
        return map;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString();
        if (s.isEmpty()) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            String json = objectMapper.writeValueAsString(o);
            return json.length() > 4000 ? null : json;
        } catch (Exception e) {
            log.debug("Dropping unserialisable event meta: {}", e.getMessage());
            return null;
        }
    }
}
