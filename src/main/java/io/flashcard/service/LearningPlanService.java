package io.flashcard.service;

import io.flashcard.model.GrammarLesson;
import io.flashcard.repository.GrammarRepository;
import io.flashcard.repository.LearningRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides what the learner should work on next (the feed's non-word cards) and how close they
 * are to passing the goal exams at their target level. Which exams are goals and which are practice
 * material is stored per exam (official_exams.plan_role); the target level per learner (learner_goals).
 */
@Service
public class LearningPlanService {


    private final SubscriberRepository subscriberRepo;
    private final GrammarRepository grammarRepo;
    private final LearningRepository learningRepo;
    private final SentWordRepository sentWordRepo;
    private final GrammarBankService grammarBank;
    private final WordBankService wordBank;
    private final GrammarPracticeService practiceService;
    private final ExamBankService examBank;
    // Thresholds, counts and the level order: all stored in learning_settings
    private final LearningSettings settings;
    private final Random random = new Random();

    public LearningPlanService(SubscriberRepository subscriberRepo, GrammarRepository grammarRepo,
                               LearningRepository learningRepo, SentWordRepository sentWordRepo,
                               GrammarBankService grammarBank, WordBankService wordBank,
                               GrammarPracticeService practiceService, ExamBankService examBank,
                               LearningSettings settings) {
        this.subscriberRepo = subscriberRepo;
        this.grammarRepo = grammarRepo;
        this.learningRepo = learningRepo;
        this.sentWordRepo = sentWordRepo;
        this.grammarBank = grammarBank;
        this.wordBank = wordBank;
        this.practiceService = practiceService;
        this.examBank = examBank;
        this.settings = settings;
    }

    private List<String> levels() {
        return settings.list("plan.level_order");
    }

    record Candidate(GrammarLesson lesson, String reason, double priority, Map<String, Object> mastery) {}

    // ── Plan ──

    public Map<String, Object> plan(long chatId, int limit, Map<String, Map<String, Object>> podcastInfo) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        boolean useTurkish = "turkish".equals(subscriberRepo.getPreferences(chatId).getNativeLanguage());
        String examLevel = targetExamLevel(chatId, level);

        List<Candidate> candidates = rankLessons(chatId, level);
        Candidate focus = candidates.isEmpty() ? null : candidates.get(0);

        List<Map<String, Object>> lessonItems = new ArrayList<>();
        List<Map<String, Object>> practiceItems = new ArrayList<>();
        if (focus != null) {
            // Teach before testing a new topic, and re-teach one the learner keeps missing.
            if (!"recheck".equals(focus.reason()) && !"untested".equals(focus.reason())) {
                lessonItems.add(lessonItem(focus, podcastInfo));
            }
            // A lesson without enough linked sentences can't be practised; borrow from the next candidate.
            int want = settings.count("plan.practice_items");
            int window = settings.count("plan.practice_candidate_window");
            for (Candidate c : candidates.subList(0, Math.min(window, candidates.size()))) {
                if (practiceItems.size() >= want) break;
                var practice = practiceService.generate(c.lesson().id(), level, useTurkish, want - practiceItems.size());
                if (practice == null) continue;
                for (var q : practice.questions()) practiceItems.add(practiceItem(c, q, useTurkish));
                if (c != focus && practiceItems.size() > 0) break;
            }
        }
        List<Map<String, Object>> examItems = examItems(chatId, level, examLevel, focus, settings.count("plan.exam_items"));

        // Interleave so the feed alternates between rule practice and exam-style questions.
        List<Map<String, Object>> items = new ArrayList<>(lessonItems);
        Iterator<Map<String, Object>> p = practiceItems.iterator(), e = examItems.iterator();
        int perExam = Math.max(1, settings.count("plan.practice_per_exam"));
        while (p.hasNext() || e.hasNext()) {
            for (int k = 0; k < perExam && p.hasNext(); k++) items.add(p.next());
            if (e.hasNext()) items.add(e.next());
        }
        if (items.size() > limit) items = items.subList(0, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetExamLevel", examLevel);
        result.put("focus", focus == null ? null : focusMap(focus));
        result.put("items", items);
        return result;
    }

    /** Lessons worth working on now, most urgent first. Mastered lessons that aren't due are left out. */
    List<Candidate> rankLessons(long chatId, String level) {
        Set<String> read = grammarRepo.getSentGrammarIds(chatId);
        Map<String, Map<String, Object>> mastery = learningRepo.getAllMastery(chatId).stream()
            .collect(Collectors.toMap(r -> (String) r.get("grammar_lesson_id"), r -> r, (a, b) -> a));
        List<String> levels = levels();
        int userRank = levels.indexOf(level);
        double weakBelow = settings.num("grammar.weak_below");
        int practiceWeakAttempts = settings.count("grammar.practice_weak_min_attempts");
        double examWeakBase = settings.num("ranking.exam_weak_base");
        double practiceWeakBase = settings.num("ranking.practice_weak_base");
        double recheckBase = settings.num("ranking.recheck_base");
        double untestedBase = settings.num("ranking.untested_base");
        double nextUpBase = settings.num("ranking.next_up_base");
        double weaknessWeight = settings.num("ranking.weakness_weight");
        double earlierLevelBonus = settings.num("ranking.earlier_level_bonus");
        double orderStep = settings.num("ranking.teaching_order_step");

        List<Candidate> out = new ArrayList<>();
        List<GrammarLesson> lessons = grammarBank.getAllLessons();
        for (int i = 0; i < lessons.size(); i++) {
            GrammarLesson l = lessons.get(i);
            int rank = levels.indexOf(l.cefrLevel());
            var m = mastery.get(l.id());
            if (m != null) {
                double score = ((Number) m.get("mastery")).doubleValue();
                int attempts = ((Number) m.get("attempts")).intValue();
                int examMisses = ((Number) m.get("exam_misses")).intValue();
                boolean due = Boolean.TRUE.equals(m.get("due"));
                double weakness = weaknessWeight * (1 - score);
                if (examMisses > 0 && score < weakBelow) out.add(new Candidate(l, "exam_weak", examWeakBase + weakness, m));
                else if (attempts >= practiceWeakAttempts && score < weakBelow) out.add(new Candidate(l, "practice_weak", practiceWeakBase + weakness, m));
                else if (due) out.add(new Candidate(l, "recheck", recheckBase + weakness, m));
            } else if (rank <= userRank) {
                // Earlier levels first: gaps in the foundation hurt every later topic.
                double levelBias = (userRank - rank) * earlierLevelBonus;
                if (read.contains(l.id())) out.add(new Candidate(l, "untested", untestedBase + levelBias, null));
                else out.add(new Candidate(l, "next_up", nextUpBase + levelBias - i * orderStep, null));
            }
        }
        out.sort((a, b) -> Double.compare(b.priority(), a.priority()));
        return out;
    }

    private List<Map<String, Object>> examItems(long chatId, String level, String examLevel, Candidate focus, int max) {
        if (examLevel == null) return List.of();
        // Below the exam level only each exam's first (shortest) task is within reach.
        boolean firstTaskOnly = levels().indexOf(level) < levels().indexOf(examLevel);
        List<ExamBankService.Exam> exams = examBank.practiceExamIds(examLevel).stream()
            .map(examBank::getExam).filter(Objects::nonNull).toList();
        if (exams.isEmpty()) return List.of();

        Set<String> recent = learningRepo.getRecentExamItemIds(chatId, settings.count("plan.exam_recent_days"));
        Map<String, Double> skillAccuracy = skillAccuracy(chatId, examLevel);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> picked = new HashSet<>();

        // 1. Retry one question missed at least 3 days ago: the second attempt shows whether it stuck.
        for (String itemId : learningRepo.getMissedExamItemIds(chatId, settings.count("plan.exam_retry_after_days"))) {
            var ref = resolve(exams, itemId);
            if (ref == null || (firstTaskOnly && !ref.exam().isFirstTask(ref.q()))) continue;
            out.add(examItem(ref.exam(), ref.q(), "exam_weak"));
            picked.add(itemId);
            break;
        }

        // 2. Fresh questions: those testing the focus rule first, then the weakest exam skill (reading,
        //    listening, writing), then the weakest tagged sub-skill.
        record Scored(ExamBankService.Exam exam, ExamBankService.Question q, double score) {}
        List<Scored> fresh = new ArrayList<>();
        double jitter = settings.num("exam_pick.random_jitter");
        double focusBonus = settings.num("exam_pick.focus_rule_bonus");
        double examSkillWeight = settings.num("exam_pick.exam_skill_weight");
        double subSkillWeight = settings.num("exam_pick.sub_skill_weight");
        double unknownAccuracy = settings.num("exam_pick.unknown_skill_accuracy");
        for (var exam : exams) {
            for (var q : exam.questions()) {
                String id = exam.id() + "#" + q.index();
                if (recent.contains(id) || picked.contains(id)) continue;
                if (firstTaskOnly && !exam.isFirstTask(q)) continue;
                var tag = exam.tag(q.index());
                double score = random.nextDouble() * jitter;
                if (focus != null && tag.grammarLessonIds().contains(focus.lesson().id())) score += focusBonus;
                if (exam.skill() != null) score += examSkillWeight * (1 - skillAccuracy.getOrDefault(exam.skill(), unknownAccuracy));
                if (tag.skill() != null) score += subSkillWeight * (1 - skillAccuracy.getOrDefault(tag.skill(), unknownAccuracy));
                fresh.add(new Scored(exam, q, score));
            }
        }
        fresh.sort((a, b) -> Double.compare(b.score(), a.score()));
        for (var s : fresh) {
            if (out.size() >= max) break;
            out.add(examItem(s.exam(), s.q(), "untested"));
        }
        return out;
    }

    private record ExamRef(ExamBankService.Exam exam, ExamBankService.Question q) {}

    private static ExamRef resolve(List<ExamBankService.Exam> exams, String itemId) {
        int hash = itemId.indexOf('#');
        if (hash < 0) return null;
        String examId = itemId.substring(0, hash);
        int qi;
        try {
            qi = Integer.parseInt(itemId.substring(hash + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        for (var exam : exams) {
            if (exam.id().equals(examId) && exam.question(qi) != null) {
                return new ExamRef(exam, exam.question(qi));
            }
        }
        return null;
    }

    /**
     * The level whose goal exams the learner is working toward: the one the placement test (or the
     * learner) chose, else their profile level or the lowest exam level above it.
     */
    String targetExamLevel(long chatId, String level) {
        Set<String> available = examBank.examLevels();
        var goal = learningRepo.getGoal(chatId);
        if (goal != null && available.contains((String) goal.get("target_level"))) return (String) goal.get("target_level");
        List<String> levels = levels();
        int rank = levels.indexOf(level);
        for (int r = Math.max(rank, 0); r < levels.size(); r++) {
            if (available.contains(levels.get(r))) return levels.get(r);
        }
        return null;
    }

    /** The learner sets their own goal level; null when the level has no goal exams. */
    public Map<String, Object> setGoal(long chatId, String targetLevel) {
        if (targetLevel == null || !examBank.examLevels().contains(targetLevel)) return null;
        learningRepo.saveGoal(chatId, targetLevel, "learner");
        return Map.of("targetLevel", targetLevel, "source", "learner");
    }

    // ── Readiness ──

    public Map<String, Object> readiness(long chatId) {
        String level = subscriberRepo.getSubscriberLevel(chatId);
        String examLevel = targetExamLevel(chatId, level);
        var goal = learningRepo.getGoal(chatId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetLevel", examLevel != null ? examLevel : level);
        result.put("targetSource", goal != null && examLevel != null && examLevel.equals(goal.get("target_level"))
            ? goal.get("source") : "profile");
        result.put("availableLevels", examBank.examLevels());

        // Evidence: the latest answer to every goal and practice question at the target level, grouped by the
        // exam's skill; reading also breaks down by tagged sub-skill (vocabulary, detail, …).
        var answers = examLevel == null ? List.<Map<String, Object>>of()
            : learningRepo.getLatestExamAnswers(chatId, examBank.planExamIds(examLevel));
        Map<String, int[]> byExamSkill = new LinkedHashMap<>();
        Map<String, int[]> readingSub = new LinkedHashMap<>();
        for (var a : answers) {
            String examSkill = a.get("exam_skill") != null ? (String) a.get("exam_skill") : "reading";
            boolean right = Boolean.TRUE.equals(a.get("is_correct"));
            tally(byExamSkill, examSkill, right);
            Object sub = a.get("skill");
            if ("reading".equals(examSkill) && sub != null && !examSkill.equals(sub)) tally(readingSub, (String) sub, right);
        }

        Map<String, String> goals = examLevel != null ? examBank.goalExams(examLevel) : Map.of();
        LinkedHashSet<String> skillOrder = new LinkedHashSet<>(goals.keySet());
        skillOrder.addAll(byExamSkill.keySet());
        List<Map<String, Object>> skills = new ArrayList<>();
        for (String skill : skillOrder) {
            int[] c = byExamSkill.getOrDefault(skill, new int[2]);
            String goalExam = goals.get(skill);
            skills.add(skillReadiness(chatId, skill, goalExam, c[0], c[1]));
        }
        result.put("skills", skills);
        result.put("tasks", examLevel == null ? List.of() : learningRepo.getTaskProgress(chatId, examLevel));

        // Back-compatible reading summary and single "mock" for app versions that know only reading
        Map<String, Object> readingSkill = skills.stream().filter(sk -> "reading".equals(sk.get("skill"))).findFirst().orElse(null);
        Map<String, Object> reading = new LinkedHashMap<>();
        reading.put("estimatedScore", readingSkill != null ? readingSkill.get("estimatedScore") : null);
        reading.put("evidence", readingSkill != null ? readingSkill.get("evidence") : 0);
        reading.put("passMark", readingSkill != null ? readingSkill.get("passMark") : null);
        reading.put("skills", readingSub.entrySet().stream()
            .map(e -> Map.<String, Object>of("skill", e.getKey(), "correct", e.getValue()[0], "total", e.getValue()[1],
                "accuracy", (double) e.getValue()[0] / e.getValue()[1]))
            .sorted(Comparator.comparingDouble(m -> (Double) m.get("accuracy")))
            .toList());
        result.put("reading", reading);

        result.put("grammar", grammarSummary(chatId, examLevel != null ? examLevel : level));
        result.put("vocabulary", vocabularySummary(chatId, level));

        // Overall: take a goal exam as soon as one skill is ready for it, the weakest-evidence one first
        var ready = skills.stream().filter(sk -> "take_mock".equals(sk.get("recommendation")) && sk.get("examId") != null).findFirst();
        boolean anyData = skills.stream().anyMatch(sk -> !"not_enough_data".equals(sk.get("recommendation")));
        result.put("recommendation", ready.isPresent() ? "take_mock" : anyData ? "keep_training" : "not_enough_data");
        Map<String, Object> mockSkill = ready.orElse(readingSkill != null ? readingSkill
            : skills.stream().filter(sk -> sk.get("examId") != null).findFirst().orElse(null));
        result.put("mockExamId", mockSkill != null ? mockSkill.get("examId") : null);
        result.put("lastMock", mockSkill != null ? mockSkill.get("lastAttempt") : null);
        return result;
    }

    /**
     * For a goal exam: the learner's readiness estimate for its skill at its level, from the evidence so far.
     * Taken just before grading, so the prediction can later be compared with the actual score. Null for
     * other exams or when there is no evidence yet.
     */
    public Double predictedScore(long chatId, String examId) {
        var row = examBank.listExams().stream().filter(e -> examId.equals(e.get("id"))).findFirst().orElse(null);
        if (row == null || !ExamBankService.ROLE_GOAL.equals(row.get("plan_role"))) return null;
        String level = (String) row.get("cefr_level");
        String skill = row.get("skill") != null ? (String) row.get("skill") : "reading";
        int correct = 0, n = 0;
        for (var a : learningRepo.getLatestExamAnswers(chatId, examBank.planExamIds(level))) {
            String examSkill = a.get("exam_skill") != null ? (String) a.get("exam_skill") : "reading";
            if (!skill.equals(examSkill)) continue;
            n++;
            if (Boolean.TRUE.equals(a.get("is_correct"))) correct++;
        }
        return n == 0 ? null : estimate(correct, n);
    }

    private Map<String, Object> skillReadiness(long chatId, String skill, String goalExam, int correct, int n) {
        Double estimate = n == 0 ? null : estimate(correct, n);
        Double passMark = examBank.passMark(goalExam);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("skill", skill);
        out.put("examId", goalExam);
        out.put("estimatedScore", estimate);
        out.put("evidence", n);
        out.put("passMark", passMark);
        out.put("recommendation", n < settings.count("readiness.min_evidence") ? "not_enough_data"
            : estimate >= settings.num("readiness.mock_threshold") && n >= settings.count("readiness.mock_evidence")
                ? "take_mock" : "keep_training");
        Map<String, Object> last = null;
        if (goalExam != null) {
            var a = learningRepo.getLastAttempt(chatId, goalExam);
            if (a != null) {
                int score = ((Number) a.get("score")).intValue();
                int total = ((Number) a.get("total")).intValue();
                double pct = total == 0 ? 0 : (double) score / total;
                last = new LinkedHashMap<>();
                last.put("examId", goalExam);
                last.put("percentage", Math.round(pct * 100));
                last.put("passed", passMark != null ? pct >= passMark : null);
                last.put("completedAt", a.get("completed_at"));
            }
        }
        out.put("lastAttempt", last);
        return out;
    }

    private static void tally(Map<String, int[]> map, String key, boolean correct) {
        int[] c = map.computeIfAbsent(key, k -> new int[2]);
        if (correct) c[0]++;
        c[1]++;
    }

    /** Accuracy with priorAnswers pseudo-answers at 50% mixed in, so a few lucky answers do not look like readiness. */
    double estimate(long correct, int n) {
        return estimate(correct, n, settings.count("readiness.prior_answers"));
    }

    static double estimate(long correct, int n, int priorAnswers) {
        return (correct + priorAnswers * 0.5) / (n + priorAnswers);
    }

    private Map<String, Object> grammarSummary(long chatId, String upToLevel) {
        List<String> levels = levels();
        int maxRank = levels.indexOf(upToLevel);
        double weakBelow = settings.num("grammar.weak_below");
        int weakAttempts = settings.count("grammar.summary_weak_min_attempts");
        double masteredScore = settings.num("grammar.mastered_score");
        int masteredAttempts = settings.count("grammar.mastered_min_attempts");
        Map<String, GrammarLesson> lessons = grammarBank.getAllLessons().stream()
            .filter(l -> levels.indexOf(l.cefrLevel()) <= maxRank)
            .collect(Collectors.toMap(GrammarLesson::id, l -> l, (a, b) -> a, LinkedHashMap::new));
        int tested = 0, mastered = 0;
        List<Map<String, Object>> weak = new ArrayList<>();
        for (var m : learningRepo.getAllMastery(chatId)) {
            var lesson = lessons.get((String) m.get("grammar_lesson_id"));
            if (lesson == null) continue;
            double score = ((Number) m.get("mastery")).doubleValue();
            int attempts = ((Number) m.get("attempts")).intValue();
            tested++;
            if (score >= masteredScore && attempts >= masteredAttempts) mastered++;
            if (score < weakBelow && attempts >= weakAttempts) {
                var w = new LinkedHashMap<String, Object>();
                w.put("lessonId", lesson.id());
                w.put("topic", lesson.topic());
                w.put("topicTr", lesson.topicTr() != null ? lesson.topicTr() : lesson.topic());
                w.put("cefrLevel", lesson.cefrLevel());
                w.put("mastery", score);
                w.put("attempts", attempts);
                weak.add(w);
            }
        }
        weak.sort(Comparator.comparingDouble(w -> (Double) w.get("mastery")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", lessons.size());
        out.put("tested", tested);
        out.put("mastered", mastered);
        out.put("weak", weak.size() > 5 ? weak.subList(0, 5) : weak);
        return out;
    }

    private Map<String, Object> vocabularySummary(long chatId, String level) {
        var r = sentWordRepo.getLevelReadiness(chatId, level);
        int strong = ((Number) r.get("strong")).intValue();
        int required = (int) Math.ceil(wordBank.getWordsForLevel(level).size() * settings.num("vocabulary.ready_share"));
        double avgEase = ((Number) r.get("avg_ease")).doubleValue();
        return Map.of("strong", strong, "required", required,
            "ready", strong >= required && avgEase >= settings.num("vocabulary.ready_avg_ease"));
    }

    /** Estimated accuracy per exam skill (reading, listening, …) and per tagged sub-skill. */
    private Map<String, Double> skillAccuracy(long chatId, String examLevel) {
        Map<String, int[]> counts = new HashMap<>();
        for (var a : learningRepo.getLatestExamAnswers(chatId, examBank.planExamIds(examLevel))) {
            boolean right = Boolean.TRUE.equals(a.get("is_correct"));
            if (a.get("exam_skill") != null) tally(counts, (String) a.get("exam_skill"), right);
            if (a.get("skill") != null && !a.get("skill").equals(a.get("exam_skill"))) tally(counts, (String) a.get("skill"), right);
        }
        Map<String, Double> out = new HashMap<>();
        counts.forEach((k, c) -> out.put(k, estimate(c[0], c[1])));
        return out;
    }

    // ── Item shapes ──

    private Map<String, Object> focusMap(Candidate c) {
        var m = new LinkedHashMap<String, Object>();
        m.put("lessonId", c.lesson().id());
        m.put("topic", c.lesson().topic());
        m.put("topicTr", c.lesson().topicTr() != null ? c.lesson().topicTr() : c.lesson().topic());
        m.put("cefrLevel", c.lesson().cefrLevel());
        m.put("mastery", c.mastery() != null ? c.mastery().get("mastery") : null);
        m.put("attempts", c.mastery() != null ? c.mastery().get("attempts") : 0);
        m.put("reason", c.reason());
        return m;
    }

    private Map<String, Object> lessonItem(Candidate c, Map<String, Map<String, Object>> podcastInfo) {
        GrammarLesson l = c.lesson();
        var lesson = new LinkedHashMap<String, Object>();
        lesson.put("id", l.id());
        lesson.put("topic", l.topic());
        lesson.put("topicTr", l.topicTr() != null ? l.topicTr() : l.topic());
        lesson.put("cefrLevel", l.cefrLevel());
        lesson.put("content", l.content());
        lesson.put("contentTr", l.contentTr() != null ? l.contentTr() : l.content());
        var podcast = podcastInfo.get(l.id());
        if (podcast != null) lesson.put("podcastUrl", podcast.get("podcastUrl"));
        lesson.put("hasVideo", podcast != null && Boolean.TRUE.equals(podcast.get("hasVideo")));
        lesson.put("mastery", c.mastery() != null ? c.mastery().get("mastery") : null);
        lesson.put("attempts", c.mastery() != null ? c.mastery().get("attempts") : 0);
        return Map.of("kind", "grammar_lesson", "reason", c.reason(), "lesson", lesson);
    }

    private Map<String, Object> practiceItem(Candidate c, Map<String, Object> q, boolean useTurkish) {
        var question = new LinkedHashMap<String, Object>();
        question.put("type", q.get("type"));
        question.put("prompt", q.get("prompt"));
        question.put("options", q.get("options"));
        question.put("correctIndex", q.get("correctIndex"));
        question.put("explanation", q.get("explanation"));
        var item = new LinkedHashMap<String, Object>();
        item.put("kind", "practice_question");
        item.put("reason", c.reason());
        item.put("lessonId", c.lesson().id());
        item.put("topic", c.lesson().topic());
        item.put("topicTr", c.lesson().topicTr() != null ? c.lesson().topicTr() : c.lesson().topic());
        item.put("question", question);
        return item;
    }

    private Map<String, Object> examItem(ExamBankService.Exam exam, ExamBankService.Question q, String reason) {
        var item = new LinkedHashMap<String, Object>();
        item.put("kind", "exam_question");
        item.put("reason", reason);
        item.put("examId", exam.id());
        item.put("examTitle", exam.title());
        item.put("cefrLevel", exam.cefrLevel());
        item.put("questionIndex", q.index());
        item.put("section", LearnerModelService.sectionName(q.section()));
        if (q.context() != null) item.put("context", q.context());
        item.put("type", q.type());
        item.put("prompt", q.prompt());
        item.put("options", q.options());
        item.put("correctIndex", q.correctIndex());
        if (q.isTextGap()) item.put("accepted", q.accepted());
        if (!q.audio().isEmpty()) item.put("audio", q.audio());
        if (!q.images().isEmpty()) item.put("images", q.images());
        if (!q.explanation().isEmpty()) item.put("explanation", q.explanation());
        item.put("skill", exam.skillOf(q));
        item.put("examSkill", exam.skill());
        return item;
    }
}
