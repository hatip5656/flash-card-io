package io.flashcard.controller;

import io.flashcard.model.GrammarStory;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.StoryBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/mobile/grammar")
public class GrammarPracticeController {

    private final SubscriberRepository subscriberRepo;
    private final StoryBankService storyBankService;
    private final JdbcTemplate jdbc;

    public GrammarPracticeController(SubscriberRepository subscriberRepo, StoryBankService storyBankService,
                                     JdbcTemplate jdbc) {
        this.subscriberRepo = subscriberRepo;
        this.storyBankService = storyBankService;
        this.jdbc = jdbc;
    }

    record Example(String estonian, String english, String turkish) {}

    @GetMapping("/practice")
    public ResponseEntity<?> generatePractice(HttpServletRequest request,
                                               @RequestParam(required = false) String storyId) {
        long chatId = getUserId(request);
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        boolean useTurkish = "turkish".equals(prefs.getNativeLanguage());
        String level = subscriberRepo.getSubscriberLevel(chatId);

        // Try to get examples from linked sentences first (grammar lessons)
        List<Example> targetExamples;
        List<Example> allLevelExamples;
        String topic;

        if (storyId != null) {
            // Try linked sentences for this grammar lesson
            targetExamples = getLinkedSentences(storyId);
            topic = getGrammarTopic(storyId, useTurkish);
        } else {
            targetExamples = List.of();
            topic = null;
        }

        // Get all level examples from linked sentences for distractors
        allLevelExamples = getAllLevelLinkedSentences(level);

        // If no linked sentences found, fall back to story-based examples
        if (targetExamples.isEmpty()) {
            List<GrammarStory> allStories = storyBankService.getAllStories();
            List<GrammarStory> levelStories = allStories.stream().filter(s -> s.getCefrLevel().equals(level)).toList();
            List<GrammarStory> targetStories = storyId != null
                ? allStories.stream().filter(s -> s.getId().equals(storyId)).toList()
                : levelStories;

            if (!targetStories.isEmpty()) {
                if (topic == null) topic = targetStories.get(0).getTopic();
                targetExamples = extractStoryExamples(targetStories);
            }

            if (allLevelExamples.isEmpty()) {
                allLevelExamples = extractStoryExamples(levelStories);
            }
        }

        if (topic == null) topic = useTurkish ? "Dilbilgisi Alıştırması" : "Grammar Practice";

        if (targetExamples.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error",
                useTurkish ? "Yeterli alıştırma verisi yok." : "Not enough practice data."));
        }

        // Merge target examples into allLevel for richer distractors
        Set<String> targetEstonians = targetExamples.stream().map(e -> e.estonian).collect(Collectors.toSet());
        List<Example> combined = new ArrayList<>(allLevelExamples);
        for (Example ex : targetExamples) {
            if (combined.stream().noneMatch(e -> e.estonian.equals(ex.estonian))) {
                combined.add(ex);
            }
        }
        allLevelExamples = combined;

        java.util.function.Function<Example, String> nativeTrans = ex ->
            useTurkish && ex.turkish != null && !ex.turkish.isBlank() ? ex.turkish : ex.english;

        List<Map<String, Object>> questions = new ArrayList<>();
        int idx = 0;
        Random rnd = new Random();
        final String topicFinal = topic;

        // Round 1: Translate native -> Estonian
        for (Example ex : targetExamples) {
            String correct = ex.estonian;
            List<String> distractors = new ArrayList<>(allLevelExamples.stream()
                .filter(e -> !e.estonian.equals(correct))
                .map(e -> e.estonian).collect(Collectors.toList()));
            Collections.shuffle(distractors);
            distractors = distractors.subList(0, Math.min(3, distractors.size()));
            if (distractors.size() < 2) continue;
            while (distractors.size() < 3) distractors.add("\u2014");
            List<String> options = new ArrayList<>(distractors);
            options.add(correct);
            Collections.shuffle(options);
            questions.add(Map.of("index", idx++, "type", "translate", "prompt", nativeTrans.apply(ex),
                "options", options, "correctIndex", options.indexOf(correct),
                "explanation", ex.estonian + " = " + nativeTrans.apply(ex), "storyTopic", topicFinal));
        }

        // Round 2: Estonian -> native
        for (Example ex : targetExamples) {
            String correct = nativeTrans.apply(ex);
            List<String> distractors = new ArrayList<>(allLevelExamples.stream()
                .filter(e -> !nativeTrans.apply(e).equals(correct))
                .map(nativeTrans).collect(Collectors.toList()));
            Collections.shuffle(distractors);
            distractors = distractors.subList(0, Math.min(3, distractors.size()));
            if (distractors.size() < 2) continue;
            while (distractors.size() < 3) distractors.add("\u2014");
            List<String> options = new ArrayList<>(distractors);
            options.add(correct);
            Collections.shuffle(options);
            questions.add(Map.of("index", idx++, "type", "choose-form", "prompt", ex.estonian,
                "options", options, "correctIndex", options.indexOf(correct),
                "explanation", ex.estonian + " = " + correct, "storyTopic", topicFinal));
        }

        // Round 3: Fill in the blank
        for (Example ex : targetExamples) {
            String[] words = ex.estonian.split("\\s+");
            if (words.length < 3) continue;
            Map<String, Integer> wordFreq = new HashMap<>();
            for (Example other : targetExamples) {
                for (String w : other.estonian.split("\\s+")) {
                    wordFreq.merge(w.toLowerCase(), 1, Integer::sum);
                }
            }
            int targetIdx = 0;
            int maxFreq = 0;
            for (int i = 0; i < words.length; i++) {
                if (words[i].length() > 1) {
                    int freq = wordFreq.getOrDefault(words[i].toLowerCase(), 0);
                    if (freq > maxFreq) { maxFreq = freq; targetIdx = i; }
                }
            }
            String targetWord = words[targetIdx].toLowerCase();
            String[] blanked = Arrays.copyOf(words, words.length);
            blanked[targetIdx] = "____";
            String prompt = String.join(" ", blanked);

            Set<String> otherGrammar = new HashSet<>();
            for (Example other : allLevelExamples) {
                for (String w : other.estonian.split("\\s+")) {
                    if (w.length() > 1 && !w.toLowerCase().equals(targetWord)) otherGrammar.add(w.toLowerCase());
                }
            }
            List<String> distractors = new ArrayList<>(otherGrammar);
            Collections.shuffle(distractors);
            distractors = distractors.subList(0, Math.min(3, distractors.size()));
            while (distractors.size() < 3) distractors.add("\u2014");
            List<String> options = new ArrayList<>(distractors);
            options.add(targetWord);
            Collections.shuffle(options);
            questions.add(Map.of("index", idx++, "type", "fill-blank", "prompt", prompt,
                "options", options, "correctIndex", options.indexOf(targetWord),
                "explanation", ex.estonian, "storyTopic", topicFinal));
        }

        Collections.shuffle(questions);
        List<Map<String, Object>> capped = questions.subList(0, Math.min(15, questions.size()));
        List<Map<String, Object>> reindexed = new ArrayList<>();
        for (int i = 0; i < capped.size(); i++) {
            Map<String, Object> q = new LinkedHashMap<>(capped.get(i));
            q.put("index", i);
            reindexed.add(q);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalQuestions", reindexed.size());
        response.put("questions", reindexed);
        response.put("storyId", storyId);
        return ResponseEntity.ok(response);
    }

    private List<Example> getLinkedSentences(String grammarLessonId) {
        try {
            var rows = jdbc.queryForList("""
                SELECT sgl.sentence_estonian, ws.english, ws.turkish
                FROM sentence_grammar_links sgl
                JOIN word_sentences ws ON ws.word_id = sgl.word_id AND ws.estonian = sgl.sentence_estonian
                WHERE sgl.grammar_lesson_id = ? AND sgl.confidence >= 0.7
                ORDER BY sgl.confidence DESC
                LIMIT 20
                """, grammarLessonId);
            return rows.stream()
                .filter(r -> r.get("sentence_estonian") != null && r.get("english") != null)
                .map(r -> new Example(
                    (String) r.get("sentence_estonian"),
                    (String) r.get("english"),
                    (String) r.get("turkish")))
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Example> getAllLevelLinkedSentences(String level) {
        try {
            var rows = jdbc.queryForList("""
                SELECT sgl.sentence_estonian, ws.english, ws.turkish
                FROM sentence_grammar_links sgl
                JOIN word_sentences ws ON ws.word_id = sgl.word_id AND ws.estonian = sgl.sentence_estonian
                JOIN grammar_lessons gl ON gl.id = sgl.grammar_lesson_id
                WHERE gl.cefr_level = ? AND sgl.confidence >= 0.7
                ORDER BY RANDOM()
                LIMIT 100
                """, level);
            return rows.stream()
                .filter(r -> r.get("sentence_estonian") != null && r.get("english") != null)
                .map(r -> new Example(
                    (String) r.get("sentence_estonian"),
                    (String) r.get("english"),
                    (String) r.get("turkish")))
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String getGrammarTopic(String grammarLessonId, boolean useTurkish) {
        try {
            var rows = jdbc.queryForList(
                "SELECT topic, topic_tr FROM grammar_lessons WHERE id = ?", grammarLessonId);
            if (rows.isEmpty()) return null;
            String topicTr = (String) rows.get(0).get("topic_tr");
            String topic = (String) rows.get(0).get("topic");
            return useTurkish && topicTr != null && !topicTr.isBlank() ? topicTr : topic;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Example> extractStoryExamples(List<GrammarStory> stories) {
        List<Example> examples = new ArrayList<>();
        for (GrammarStory s : stories) {
            for (GrammarStory.Slide slide : s.getSlides()) {
                if (slide.examples() != null) {
                    slide.examples().stream()
                        .filter(e -> e.estonian() != null && e.english() != null)
                        .forEach(e -> examples.add(new Example(e.estonian(), e.english(), e.turkish())));
                }
            }
        }
        return examples;
    }
}
