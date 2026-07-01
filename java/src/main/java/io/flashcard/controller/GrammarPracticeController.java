package io.flashcard.controller;

import io.flashcard.model.GrammarStory;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.StoryBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/mobile/grammar")
public class GrammarPracticeController {

    private final SubscriberRepository subscriberRepo;
    private final StoryBankService storyBankService;

    public GrammarPracticeController(SubscriberRepository subscriberRepo, StoryBankService storyBankService) {
        this.subscriberRepo = subscriberRepo;
        this.storyBankService = storyBankService;
    }

    @GetMapping("/practice")
    public ResponseEntity<?> generatePractice(HttpServletRequest request,
                                               @RequestParam(required = false) String storyId) {
        long chatId = getUserId(request);
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        boolean useTurkish = "turkish".equals(prefs.getNativeLanguage());
        String level = subscriberRepo.getSubscriberLevel(chatId);

        List<GrammarStory> allStories = storyBankService.getAllStories();
        List<GrammarStory> levelStories = allStories.stream().filter(s -> s.getCefrLevel().equals(level)).toList();
        List<GrammarStory> targetStories = storyId != null
            ? allStories.stream().filter(s -> s.getId().equals(storyId)).toList()
            : levelStories;

        if (targetStories.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No stories found"));
        }

        // Collect examples
        record Example(String estonian, String english, String turkish) {}
        List<Example> allLevelExamples = new ArrayList<>();
        for (GrammarStory s : levelStories) {
            for (GrammarStory.Slide slide : s.getSlides()) {
                if (slide.examples() != null) {
                    slide.examples().forEach(e -> allLevelExamples.add(new Example(e.estonian(), e.english(), e.turkish())));
                }
            }
        }

        List<Example> storyExamples = new ArrayList<>();
        String topic = targetStories.get(0).getTopic();
        for (GrammarStory s : targetStories) {
            for (GrammarStory.Slide slide : s.getSlides()) {
                if (slide.examples() != null) {
                    slide.examples().forEach(e -> storyExamples.add(new Example(e.estonian(), e.english(), e.turkish())));
                }
            }
        }

        if (storyExamples.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error",
                useTurkish ? "Yeterli al\u0131\u015ft\u0131rma verisi yok." : "Not enough practice data."));
        }

        java.util.function.Function<Example, String> nativeTrans = ex ->
            useTurkish && ex.turkish != null ? ex.turkish : ex.english;

        List<Map<String, Object>> questions = new ArrayList<>();
        int idx = 0;
        Random rnd = new Random();

        // Round 1: Translate native -> Estonian
        for (Example ex : storyExamples) {
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
                "explanation", ex.estonian + " = " + nativeTrans.apply(ex), "storyTopic", topic));
        }

        // Round 2: Estonian -> native
        for (Example ex : storyExamples) {
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
                "explanation", ex.estonian + " = " + correct, "storyTopic", topic));
        }

        // Round 3: Fill in the blank
        for (Example ex : storyExamples) {
            String[] words = ex.estonian.split("\\s+");
            if (words.length < 3) continue;
            Map<String, Integer> wordFreq = new HashMap<>();
            for (Example other : storyExamples) {
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
                "explanation", ex.estonian, "storyTopic", topic));
        }

        Collections.shuffle(questions);
        List<Map<String, Object>> capped = questions.subList(0, Math.min(15, questions.size()));
        List<Map<String, Object>> reindexed = new ArrayList<>();
        for (int i = 0; i < capped.size(); i++) {
            Map<String, Object> q = new LinkedHashMap<>(capped.get(i));
            q.put("index", i);
            reindexed.add(q);
        }

        return ResponseEntity.ok(Map.of("totalQuestions", reindexed.size(), "questions", reindexed,
            "storyId", storyId != null ? storyId : (Object) null));
    }
}
