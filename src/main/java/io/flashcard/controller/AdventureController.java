package io.flashcard.controller;

import io.flashcard.repository.AdventureRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/adventure/stories")
public class AdventureController {

    private final AdventureRepository adventureRepo;

    public AdventureController(AdventureRepository adventureRepo) {
        this.adventureRepo = adventureRepo;
    }

    @GetMapping
    public Map<String, Object> listStories(HttpServletRequest request) {
        long userId = getUserId(request);
        List<Map<String, Object>> storyRows = adventureRepo.listStories();
        Map<String, Object> progressMap = adventureRepo.getUserProgress(userId);

        List<Map<String, Object>> stories = storyRows.stream().map(s -> {
            Map<String, Object> story = new LinkedHashMap<>();
            story.put("id", s.get("id"));
            story.put("title", s.get("title"));
            story.put("subtitle_tr", s.get("subtitle_tr"));
            story.put("subtitle_en", s.get("subtitle_en"));
            story.put("genre_tr", s.get("genre_tr"));
            story.put("genre_en", s.get("genre_en"));
            story.put("emoji", s.get("emoji"));
            story.put("color", s.get("color"));
            story.put("cefr_level", s.get("cefr_level"));
            story.put("nodeCount", ((Number) s.get("node_count")).intValue());
            story.put("progress", progressMap.getOrDefault(s.get("id"), null));
            return story;
        }).toList();

        return Map.of("stories", stories);
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<?> getStory(@PathVariable String storyId) {
        Map<String, Object> story = adventureRepo.getStory(storyId);
        if (story == null) return ResponseEntity.status(404).body(Map.of("error", "Story not found"));

        List<Map<String, Object>> nodeRows = adventureRepo.getNodes(storyId);
        List<Map<String, Object>> vocabRows = adventureRepo.getVocabulary(storyId);
        List<Map<String, Object>> choiceRows = adventureRepo.getChoices(storyId);
        List<Map<String, Object>> minigameRows = adventureRepo.getMinigames(storyId);

        // Build vocab map
        Map<String, List<Map<String, Object>>> vocabMap = new HashMap<>();
        for (var v : vocabRows) {
            vocabMap.computeIfAbsent((String) v.get("node_id"), k -> new ArrayList<>()).add(Map.of(
                "word", v.get("word"), "translation", v.get("translation"),
                "context_hint", v.get("context_hint"), "word_id", v.get("word_id")));
        }

        // Build choices map
        Map<String, List<Map<String, Object>>> choicesMap = new HashMap<>();
        for (var c : choiceRows) {
            choicesMap.computeIfAbsent((String) c.get("node_id"), k -> new ArrayList<>()).add(Map.of(
                "choice_id", c.get("id"), "text_ee", c.get("text_ee"), "text_tr", c.get("text_tr"),
                "is_correct_grammar", c.get("is_correct_grammar"),
                "feedback_ee", c.get("feedback_ee"), "feedback_tr", c.get("feedback_tr"),
                "nextNode_id", c.get("next_node_id")));
        }

        // Build minigame map
        Map<String, Map<String, Object>> minigameMap = new HashMap<>();
        for (var m : minigameRows) {
            minigameMap.put((String) m.get("node_id"), Map.of(
                "isActive", true, "game_type", m.get("game_type"),
                "target_words", m.get("target_words"),
                "completion_node_id", m.get("completion_node_id")));
        }
        Map<String, Object> noGame = Map.of("isActive", false, "game_type", "", "target_words", List.of(), "completion_node_id", "");

        List<Map<String, Object>> nodes = nodeRows.stream().map(n -> {
            String nodeId = (String) n.get("id");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("node_id", nodeId);
            node.put("stage", n.get("stage"));
            node.put("language_level", n.get("language_level"));
            node.put("speaker", n.get("speaker"));
            node.put("scene", n.get("scene"));
            node.put("text_ee", n.get("text_ee"));
            node.put("text_tr", n.get("text_tr"));
            node.put("vocabulary_focus", vocabMap.getOrDefault(nodeId, List.of()));
            node.put("choices", choicesMap.getOrDefault(nodeId, List.of()));
            node.put("minigame_trigger", minigameMap.getOrDefault(nodeId, noGame));
            return node;
        }).toList();

        String firstNodeId = nodes.isEmpty() ? "" : (String) nodes.get(0).get("node_id");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", story.get("id"));
        result.put("title", story.get("title"));
        result.put("subtitle_tr", story.get("subtitle_tr"));
        result.put("subtitle_en", story.get("subtitle_en"));
        result.put("genre_tr", story.get("genre_tr"));
        result.put("genre_en", story.get("genre_en"));
        result.put("emoji", story.get("emoji"));
        result.put("color", story.get("color"));
        result.put("cefr_level", story.get("cefr_level"));
        result.put("firstNodeId", firstNodeId);
        result.put("nodeCount", nodes.size());
        result.put("nodes", nodes);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{storyId}/progress")
    public Map<String, Object> saveProgress(HttpServletRequest request, @PathVariable String storyId,
                                             @RequestBody Map<String, Object> body) {
        long userId = getUserId(request);
        String currentNodeId = (String) body.get("currentNodeId");
        int wordsLearned = body.get("wordsLearned") != null ? ((Number) body.get("wordsLearned")).intValue() : 0;
        boolean completed = Boolean.TRUE.equals(body.get("completed"));
        adventureRepo.saveProgress(userId, storyId, currentNodeId, wordsLearned, completed);
        return Map.of("ok", true);
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    record ProgressResponse(Object progress) {}

    @GetMapping("/{storyId}/progress")
    public ProgressResponse getProgress(HttpServletRequest request, @PathVariable String storyId) {
        long userId = getUserId(request);
        Map<String, Object> progress = adventureRepo.getProgress(userId, storyId);
        return new ProgressResponse(progress);
    }
}
