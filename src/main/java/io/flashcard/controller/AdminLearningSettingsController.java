package io.flashcard.controller;

import io.flashcard.service.LearningSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Tuning values of the learner model, planner and feed (learning_settings), edited without a deploy. */
@RestController
@RequestMapping("/api/admin/learning-settings")
public class AdminLearningSettingsController {

    private final LearningSettings settings;

    public AdminLearningSettingsController(LearningSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("settings", settings.all());
    }

    /** Body: {value}. The value keeps its type: a number stays a number, a list stays a list. */
    @PutMapping("/{key}")
    public ResponseEntity<?> update(@PathVariable String key, @RequestBody Map<String, Object> body) throws Exception {
        if (!body.containsKey("value") || body.get("value") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "value required"));
        }
        try {
            if (!settings.update(key, body.get("value"))) {
                return ResponseEntity.status(404).body(Map.of("error", "Unknown setting " + key));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("ok", true, "key", key, "value", body.get("value")));
    }
}
