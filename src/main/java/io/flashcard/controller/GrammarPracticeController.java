package io.flashcard.controller;

import io.flashcard.model.UserPreferences;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.GrammarPracticeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/mobile/grammar")
public class GrammarPracticeController {

    private final SubscriberRepository subscriberRepo;
    private final GrammarPracticeService practiceService;

    public GrammarPracticeController(SubscriberRepository subscriberRepo, GrammarPracticeService practiceService) {
        this.subscriberRepo = subscriberRepo;
        this.practiceService = practiceService;
    }

    @GetMapping("/practice")
    public ResponseEntity<?> generatePractice(HttpServletRequest request,
                                               @RequestParam(required = false) String storyId) {
        long chatId = getUserId(request);
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        boolean useTurkish = "turkish".equals(prefs.getNativeLanguage());
        String level = subscriberRepo.getSubscriberLevel(chatId);

        var practice = practiceService.generate(storyId, level, useTurkish, 15);
        if (practice == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                useTurkish ? "Yeterli alıştırma verisi yok." : "Not enough practice data."));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalQuestions", practice.questions().size());
        response.put("questions", practice.questions());
        response.put("storyId", storyId);
        return ResponseEntity.ok(response);
    }
}
