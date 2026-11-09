package io.flashcard.controller;

import io.flashcard.repository.SentWordRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * "Did you master this word?" After every 10 correct answers to a word the app asks. Yes masters it;
 * not yet asks again after 10 more. Words answered in the widget wait here until the app is opened.
 */
@RestController
@RequestMapping("/api/users/me/mastery-prompts")
public class MasteryPromptController {

    static final int ASK_EVERY = 10;
    private static final int MAX_PROMPTS = 3;

    private final SentWordRepository words;

    public MasteryPromptController(SentWordRepository words) {
        this.words = words;
    }

    @GetMapping
    public List<Map<String, Object>> prompts(HttpServletRequest request) {
        return words.masteryPrompts(UserController.getUserId(request), MAX_PROMPTS);
    }

    @PostMapping("/{wordId}")
    public ResponseEntity<?> answer(HttpServletRequest request, @PathVariable String wordId,
                                    @RequestBody Map<String, Object> body) {
        if (!(body.get("mastered") instanceof Boolean mastered)) {
            return ResponseEntity.badRequest().body(Map.of("error", "mastered must be true or false"));
        }
        long chatId = UserController.getUserId(request);
        if (mastered) words.markWordMastered(chatId, wordId);
        else words.postponeMasteryPrompt(chatId, wordId, ASK_EVERY);
        return ResponseEntity.noContent().build();
    }
}
