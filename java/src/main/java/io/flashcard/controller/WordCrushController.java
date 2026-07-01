package io.flashcard.controller;

import io.flashcard.model.UserPreferences;
import io.flashcard.model.Word;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/mobile/word-crush")
public class WordCrushController {

    private static final Map<String, int[]> DIFFICULTY_CONFIG = Map.of(
        "easy",   new int[]{8, 8, 120, 1},     // gridSize, wordCount, timer, levels(1=A1)
        "medium", new int[]{10, 12, 90, 2},     // levels(2=A1,A2)
        "hard",   new int[]{12, 16, 75, 3},     // levels(3=A1,A2,B1)
        "expert", new int[]{14, 20, 60, 4}      // levels(4=all)
    );

    private static final String[] LEVEL_ORDER = {"A1", "A2", "B1", "B2"};

    private final SubscriberRepository subscriberRepo;
    private final WordBankService wordBankService;

    public WordCrushController(SubscriberRepository subscriberRepo, WordBankService wordBankService) {
        this.subscriberRepo = subscriberRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping
    public Map<String, Object> getWordCrushData(HttpServletRequest request,
                                                 @RequestParam(defaultValue = "easy") String difficulty) {
        long chatId = getUserId(request);
        int[] config = DIFFICULTY_CONFIG.getOrDefault(difficulty, DIFFICULTY_CONFIG.get("easy"));
        int gridSize = config[0], wordCount = config[1], timer = config[2], levelCount = config[3];

        UserPreferences prefs = subscriberRepo.getPreferences(chatId);

        List<Map<String, Object>> pool = new ArrayList<>();
        for (int i = 0; i < levelCount && i < LEVEL_ORDER.length; i++) {
            for (Word w : wordBankService.getWordsForLevel(LEVEL_ORDER[i])) {
                String est = w.getEstonian().toLowerCase();
                if (est.length() <= gridSize && est.length() >= 3 && !est.contains(" ")) {
                    pool.add(Map.of(
                        "estonian", est,
                        "english", w.getEnglish(),
                        "turkish", w.getTurkish() != null ? w.getTurkish() : "",
                        "cefrLevel", w.getCefrLevel()));
                }
            }
        }

        Collections.shuffle(pool);
        List<Map<String, Object>> shortW = pool.stream().filter(w -> ((String) w.get("estonian")).length() <= 4).collect(Collectors.toList());
        List<Map<String, Object>> mediumW = pool.stream().filter(w -> { int l = ((String) w.get("estonian")).length(); return l >= 5 && l <= 6; }).collect(Collectors.toList());
        List<Map<String, Object>> longW = pool.stream().filter(w -> ((String) w.get("estonian")).length() >= 7).collect(Collectors.toList());

        int shortCount = (int) Math.ceil(wordCount * 0.4);
        int mediumCount = (int) Math.ceil(wordCount * 0.4);

        List<Map<String, Object>> selected = new ArrayList<>();
        Set<String> selectedSet = new HashSet<>();

        addUnique(selected, selectedSet, shortW, shortCount);
        addUnique(selected, selectedSet, mediumW, mediumCount);
        addUnique(selected, selectedSet, longW, wordCount - selected.size());

        // Fill remaining
        for (Map<String, Object> w : pool) {
            if (selected.size() >= wordCount) break;
            if (selectedSet.add((String) w.get("estonian"))) selected.add(w);
        }

        Map<String, Map<String, Object>> validWords = new LinkedHashMap<>();
        for (Map<String, Object> w : pool) {
            validWords.put((String) w.get("estonian"), Map.of(
                "english", w.get("english"),
                "turkish", w.get("turkish"),
                "cefrLevel", w.get("cefrLevel")));
        }

        return Map.of(
            "difficulty", difficulty,
            "gridSize", gridSize,
            "timer", timer,
            "words", selected,
            "validWords", validWords,
            "nativeLanguage", prefs.getNativeLanguage());
    }

    private void addUnique(List<Map<String, Object>> selected, Set<String> seen,
                           List<Map<String, Object>> source, int count) {
        for (Map<String, Object> w : source) {
            if (count <= 0) break;
            if (seen.add((String) w.get("estonian"))) {
                selected.add(w);
                count--;
            }
        }
    }
}
