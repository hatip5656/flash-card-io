package io.flashcard.controller;

import io.flashcard.repository.PhraseRepository;
import io.flashcard.repository.SubscriberRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/phrases")
public class PhraseController {

    private final PhraseRepository phraseRepo;
    private final SubscriberRepository subscriberRepo;

    public PhraseController(PhraseRepository phraseRepo, SubscriberRepository subscriberRepo) {
        this.phraseRepo = phraseRepo;
        this.subscriberRepo = subscriberRepo;
    }

    @GetMapping
    public Map<String, Object> getPhrases(
            HttpServletRequest request,
            @RequestParam(defaultValue = "5") int limit) {

        long userId = (long) request.getAttribute("userId");
        String userLevel = subscriberRepo.getSubscriberLevel(userId);
        if (userLevel == null) userLevel = "A1";

        int safeLimit = Math.min(limit, 20);
        List<Map<String, Object>> rows = phraseRepo.getPhrasesForUser(userId, userLevel, safeLimit);

        List<Map<String, Object>> phrases = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> phrase = new LinkedHashMap<>();
            phrase.put("id", r.get("id"));
            phrase.put("type", "phrase");
            phrase.put("estonian", r.get("estonian"));
            phrase.put("english", r.get("english"));
            phrase.put("turkish", r.get("turkish"));
            phrase.put("category", r.get("category"));
            phrase.put("cefrLevel", r.get("cefr_level"));
            phrase.put("contextNote", r.get("context_note"));
            phrase.put("seen", r.get("seen_at") != null);
            phrases.add(phrase);
        }

        return Map.of("phrases", phrases);
    }

    @PostMapping("/{phraseId}/seen")
    public Map<String, Object> markSeen(
            HttpServletRequest request,
            @PathVariable int phraseId) {

        long userId = (long) request.getAttribute("userId");
        phraseRepo.markSeen(userId, phraseId);
        return Map.of("ok", true);
    }
}
