package io.flashcard.controller;

import io.flashcard.model.DailyGoalSettings;
import io.flashcard.repository.DailyGoalRepository;
import io.flashcard.service.DailyGoalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Today's goal, the learner's goal settings, and the devices that receive goal notifications. */
@RestController
@RequestMapping("/api/users/me")
public class DailyGoalController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DailyGoalService goals;
    private final DailyGoalRepository repo;

    public DailyGoalController(DailyGoalService goals, DailyGoalRepository repo) {
        this.goals = goals;
        this.repo = repo;
    }

    // GET /api/users/me/daily-goal
    @GetMapping("/daily-goal")
    public Map<String, Object> today(HttpServletRequest request) {
        var t = goals.today(UserController.getUserId(request));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", t.date().toString());
        out.put("tasks", t.tasks().stream().map(task -> Map.of(
            "key", task.key(), "target", task.target(), "done", task.done(), "enabled", task.enabled())).toList());
        out.put("grammar", t.grammarLessonId() == null ? null : grammar(t));
        out.put("complete", t.complete());
        out.put("streak", t.streak());
        return out;
    }

    // GET /api/users/me/daily-goal/settings
    @GetMapping("/daily-goal/settings")
    public Map<String, Object> settings(HttpServletRequest request) {
        return toJson(goals.settings(UserController.getUserId(request)));
    }

    // PUT /api/users/me/daily-goal/settings
    @PutMapping("/daily-goal/settings")
    public ResponseEntity<?> saveSettings(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            var s = new DailyGoalSettings(
                bool(body, "wordsEnabled"), integer(body, "wordsTarget"),
                bool(body, "quizEnabled"), integer(body, "quizTarget"),
                bool(body, "reviewsEnabled"), bool(body, "grammarEnabled"), bool(body, "notifyEnabled"),
                time(body, "morningTime"), time(body, "eveningTime"));
            return ResponseEntity.ok(toJson(goals.saveSettings(UserController.getUserId(request), s)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/users/me/devices { token, platform }
    @PostMapping("/devices")
    public ResponseEntity<?> registerDevice(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        if (!(body.get("token") instanceof String token) || token.isBlank() || token.length() > 4096) {
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        }
        String platform = body.get("platform") instanceof String p ? p : null;
        repo.upsertDevice(token, UserController.getUserId(request), platform);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/users/me/devices/{token}
    @DeleteMapping("/devices/{token}")
    public ResponseEntity<?> deleteDevice(HttpServletRequest request, @PathVariable String token) {
        repo.deleteDevice(UserController.getUserId(request), token);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> grammar(DailyGoalService.Today t) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("lessonId", t.grammarLessonId());
        g.put("topic", t.grammarTopic());
        return g;
    }

    private static Map<String, Object> toJson(DailyGoalSettings s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("wordsEnabled", s.wordsEnabled());
        out.put("wordsTarget", s.wordsTarget());
        out.put("quizEnabled", s.quizEnabled());
        out.put("quizTarget", s.quizTarget());
        out.put("reviewsEnabled", s.reviewsEnabled());
        out.put("grammarEnabled", s.grammarEnabled());
        out.put("notifyEnabled", s.notifyEnabled());
        out.put("morningTime", s.morningTime().format(HH_MM));
        out.put("eveningTime", s.eveningTime().format(HH_MM));
        return out;
    }

    private static boolean bool(Map<String, Object> body, String key) {
        if (body.get(key) instanceof Boolean b) return b;
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static int integer(Map<String, Object> body, String key) {
        if (body.get(key) instanceof Number n) return n.intValue();
        throw new IllegalArgumentException(key + " must be a number");
    }

    private static LocalTime time(Map<String, Object> body, String key) {
        if (body.get(key) instanceof String s) return LocalTime.parse(s, HH_MM);
        throw new IllegalArgumentException(key + " must be HH:mm");
    }
}
