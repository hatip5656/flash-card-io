package io.flashcard.controller;

import io.flashcard.model.UserPreferences;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.ScheduleService;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");
    private static final Set<String> VALID_PREF_KEYS = Set.of(
        "audio", "voiceName", "wordForms", "grammarCards", "dailySummary", "weeklyReport", "nativeLanguage", "theme");

    private final SubscriberRepository subscriberRepo;
    private final ActivityRepository activityRepo;
    private final ScheduleService scheduleService;
    private final WordBankService wordBankService;

    public UserController(SubscriberRepository subscriberRepo, ActivityRepository activityRepo,
                          ScheduleService scheduleService, WordBankService wordBankService) {
        this.subscriberRepo = subscriberRepo;
        this.activityRepo = activityRepo;
        this.scheduleService = scheduleService;
        this.wordBankService = wordBankService;
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        Object userIdObj = body.get("userId");
        if (userIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }
        long chatId;
        try {
            chatId = Long.parseLong(String.valueOf(userIdObj));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        String channel = (String) body.getOrDefault("channel", "api");
        String username = (String) body.get("username");
        String firstName = (String) body.get("firstName");

        subscriberRepo.addSubscriber(chatId, channel, username, firstName);
        Map<String, Object> stats = activityRepo.getStats(chatId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", chatId);
        result.putAll(stats);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/users/auto")
    public ResponseEntity<Map<String, Object>> autoRegister(@RequestBody Map<String, Object> body) {
        String firstName = (String) body.get("firstName");
        String channel = (String) body.getOrDefault("channel", "mobile");
        long nextId = subscriberRepo.getNextMobileUserId();
        subscriberRepo.addSubscriber(nextId, channel, null, firstName);
        Map<String, Object> stats = activityRepo.getStats(nextId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", nextId);
        result.putAll(stats);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/users/me")
    public Map<String, Object> getUser(HttpServletRequest request) {
        long chatId = getUserId(request);
        Map<String, Object> stats = activityRepo.getStats(chatId);
        String level = (String) stats.get("level");
        String schedule = (String) stats.get("schedule");
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        String scheduleLabel = scheduleService.findLabelForCron(schedule);
        int totalForLevel = wordBankService.getWordsForLevel(level).size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", chatId);
        result.put("level", level);
        result.put("schedule", schedule);
        result.put("scheduleLabel", scheduleLabel);
        result.put("totalForLevel", totalForLevel);
        result.put("preferences", prefs);
        result.put("wordsLearned", stats.get("sent"));
        return result;
    }

    @PatchMapping("/users/me/level")
    public ResponseEntity<Map<String, Object>> setLevel(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String level = (String) body.get("level");
        if (level == null || !VALID_LEVELS.contains(level)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid level. Must be one of: A1, A2, B1, B2"));
        }
        subscriberRepo.setSubscriberLevel(chatId, level);
        int totalForLevel = wordBankService.getWordsForLevel(level).size();
        return ResponseEntity.ok(Map.of("level", level, "totalForLevel", totalForLevel));
    }

    @PatchMapping("/users/me/schedule")
    public ResponseEntity<Map<String, Object>> setSchedule(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String presetKey = (String) body.get("schedule");
        ScheduleService.SchedulePreset preset = scheduleService.getPreset(presetKey);
        if (preset == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Invalid schedule. Options: " + String.join(", ", ScheduleService.PRESETS.keySet())));
        }
        String currentCron = subscriberRepo.getSubscriberSchedule(chatId);
        if (currentCron.equals(preset.cron())) {
            return ResponseEntity.ok(Map.of("schedule", presetKey, "label", preset.label(), "changed", false));
        }
        subscriberRepo.setSubscriberSchedule(chatId, preset.cron());
        return ResponseEntity.ok(Map.of("schedule", presetKey, "label", preset.label(), "changed", true));
    }

    @GetMapping("/users/me/preferences")
    public UserPreferences getUserPreferences(HttpServletRequest request) {
        return subscriberRepo.getPreferences(getUserId(request));
    }

    @PatchMapping("/users/me/preferences")
    public ResponseEntity<?> setPreference(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String key = (String) body.get("key");
        Object value = body.get("value");

        if (key == null || !VALID_PREF_KEYS.contains(key)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Invalid key. Must be one of: " + String.join(", ", VALID_PREF_KEYS)));
        }
        if ("voiceName".equals(key) && !(value instanceof String)) {
            return ResponseEntity.badRequest().body(Map.of("error", "voiceName must be a string"));
        }
        if ("nativeLanguage".equals(key) && !"turkish".equals(value) && !"english".equals(value)) {
            return ResponseEntity.badRequest().body(Map.of("error", "nativeLanguage must be 'turkish' or 'english'"));
        }
        if ("theme".equals(key) && !"dark".equals(value) && !"light".equals(value) && !"system".equals(value)) {
            return ResponseEntity.badRequest().body(Map.of("error", "theme must be 'dark', 'light', or 'system'"));
        }
        if (!"voiceName".equals(key) && !"nativeLanguage".equals(key) && !"theme".equals(key) && !(value instanceof Boolean)) {
            return ResponseEntity.badRequest().body(Map.of("error", key + " must be a boolean"));
        }

        subscriberRepo.updatePreference(chatId, key, value);
        return ResponseEntity.ok(subscriberRepo.getPreferences(chatId));
    }

    @DeleteMapping("/users/me")
    public Map<String, Object> unsubscribe(HttpServletRequest request) {
        subscriberRepo.removeSubscriber(getUserId(request));
        return Map.of("unsubscribed", true);
    }

    @GetMapping("/schedules")
    public List<Map<String, Object>> getSchedulePresets() {
        return scheduleService.getSchedulePresets();
    }

    static long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
