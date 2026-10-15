package io.flashcard.controller;

import io.flashcard.service.GrammarBankService;
import io.flashcard.service.LearnerModelService;
import io.flashcard.service.LearningPlanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.flashcard.controller.UserController.getUserId;

/** The adaptive loop: the app reports outcomes, the planner says what to show next. */
@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private static final int MAX_EVENTS = 50;

    private final LearnerModelService learnerModel;
    private final LearningPlanService planService;
    private final GrammarBankService grammarBank;

    public LearningController(LearnerModelService learnerModel, LearningPlanService planService,
                              GrammarBankService grammarBank) {
        this.learnerModel = learnerModel;
        this.planService = planService;
        this.grammarBank = grammarBank;
    }

    @PostMapping("/events")
    public ResponseEntity<?> recordEvents(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        if (!(body.get("events") instanceof List<?> list)) {
            return ResponseEntity.badRequest().body(Map.of("error", "events array required"));
        }
        if (list.size() > MAX_EVENTS) {
            return ResponseEntity.badRequest().body(Map.of("error", "at most " + MAX_EVENTS + " events per call"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = list.stream()
            .filter(e -> e instanceof Map<?, ?>).map(e -> (Map<String, Object>) e).toList();
        var results = learnerModel.recordEvents(chatId, events);
        return ResponseEntity.ok(Map.of("ok", true, "recorded", events.size(), "results", results));
    }

    @GetMapping("/plan")
    public Map<String, Object> plan(HttpServletRequest request, @RequestParam(defaultValue = "6") int limit) {
        return planService.plan(getUserId(request), Math.max(1, Math.min(limit, 12)), grammarBank.getPodcastInfo());
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness(HttpServletRequest request) {
        return planService.readiness(getUserId(request));
    }
}
