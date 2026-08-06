package io.flashcard.controller;

import io.flashcard.model.Word;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.SavedWordRepository;
import io.flashcard.repository.SentWordRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.repository.WordDbRepository;
import io.flashcard.service.ImageService;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final SubscriberRepository subscriberRepo;
    private final SentWordRepository sentWordRepo;
    private final SavedWordRepository savedWordRepo;
    private final ActivityRepository activityRepo;
    private final WordBankService wordBankService;
    private final WordDbRepository wordDbRepo;
    private final ImageService imageService;

    public FeedController(SubscriberRepository subscriberRepo, SentWordRepository sentWordRepo,
                          SavedWordRepository savedWordRepo, ActivityRepository activityRepo,
                          WordBankService wordBankService, WordDbRepository wordDbRepo,
                          ImageService imageService) {
        this.subscriberRepo = subscriberRepo;
        this.sentWordRepo = sentWordRepo;
        this.savedWordRepo = savedWordRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
        this.wordDbRepo = wordDbRepo;
        this.imageService = imageService;
    }

    @GetMapping
    public Map<String, Object> getFeed(HttpServletRequest request,
                                        @RequestParam(defaultValue = "10") int limit,
                                        @RequestParam(defaultValue = "new:0") String cursor) {
        long chatId = getUserId(request);
        limit = Math.min(limit, 20);

        String[] parts = cursor.split(":");
        String cursorMode = "review".equals(parts[0]) ? "review" : "new";
        int cursorOffset = parts.length > 1 ? Math.max(0, Integer.parseInt(parts[1])) : 0;

        String level = subscriberRepo.getSubscriberLevel(chatId);
        List<String> sentIds = sentWordRepo.getSentWordIds(chatId);
        Set<String> savedIds = new HashSet<>(savedWordRepo.getSavedWordIds(chatId));
        List<Word> unsent = wordBankService.getUnsentUpToLevel(level, sentIds);

        List<Map<String, Object>> rawItems = new ArrayList<>();
        String nextMode = cursorMode;
        int nextOffset = cursorOffset;

        if ("new".equals(cursorMode)) {
            List<Word> slice = unsent.subList(
                Math.min(cursorOffset, unsent.size()),
                Math.min(cursorOffset + limit, unsent.size()));
            for (Word w : slice) {
                rawItems.add(Map.of("word", w, "isNew", true));
            }
            nextOffset = cursorOffset + slice.size();

            if (rawItems.size() < limit) {
                nextMode = "review";
                nextOffset = 0;
                int reviewNeeded = limit - rawItems.size();
                var seenWords = sentWordRepo.getSeenWordsByRecency(chatId, reviewNeeded, 0);
                for (var sw : seenWords) {
                    Word fullWord = wordBankService.getWordById((String) sw.get("word_id"));
                    if (fullWord != null) rawItems.add(Map.of("word", fullWord, "isNew", false));
                }
                nextOffset = seenWords.size();
            }
        } else {
            var seenWords = sentWordRepo.getSeenWordsByRecency(chatId, limit, cursorOffset);
            for (var sw : seenWords) {
                Word fullWord = wordBankService.getWordById((String) sw.get("word_id"));
                if (fullWord != null) rawItems.add(Map.of("word", fullWord, "isNew", false));
            }
            nextOffset = cursorOffset + seenWords.size();
        }

        // Get cached images
        List<String> wordIds = rawItems.stream().map(r -> ((Word) r.get("word")).getId()).toList();
        Map<String, Map<String, String>> imageCache = new HashMap<>();
        for (var row : wordDbRepo.getCachedImages(wordIds)) {
            if (row.get("image_url") != null) {
                imageCache.put((String) row.get("id"), Map.of(
                    "url", (String) row.get("image_url"),
                    "photographer", row.get("image_photographer") != null ? (String) row.get("image_photographer") : ""));
            }
        }

        List<Map<String, Object>> items = rawItems.stream().map(raw -> {
            Word word = (Word) raw.get("word");
            boolean isNew = (boolean) raw.get("isNew");
            Map<String, String> img = imageCache.get(word.getId());

            // If no cached image, fetch in background — don't block the request
            if (img == null) {
                final String wordId = word.getId();
                final String query = word.getImageQuery() != null ? word.getImageQuery() : word.getEnglish();
                Thread.ofVirtual().start(() -> {
                    ImageService.ImageResult fetched = imageService.fetchImage(query);
                    if (fetched != null) {
                        wordDbRepo.updateImageCache(wordId, fetched.url(), fetched.photographer());
                    }
                });
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("word", Map.of(
                "id", word.getId(),
                "estonian", word.getEstonian(),
                "english", word.getEnglish(),
                "turkish", word.getTurkish(),
                "cefrLevel", word.getCefrLevel(),
                "sentences", word.getSentences()));
            item.put("imageUrl", img != null ? img.get("url") : null);
            item.put("photographer", img != null ? img.get("photographer") : null);
            item.put("isSaved", savedIds.contains(word.getId()));
            item.put("isNew", isNew);
            return item;
        }).toList();

        boolean hasMore = items.size() == limit;
        String nextCursor = hasMore ? nextMode + ":" + nextOffset : null;
        return Map.of("items", items, "nextCursor", nextCursor, "hasMore", hasMore);
    }

    @PostMapping("/seen/{wordId}")
    public Map<String, Object> markSeen(HttpServletRequest request, @PathVariable String wordId,
                                         @RequestBody(required = false) Map<String, Object> body) {
        long chatId = getUserId(request);
        String estonian = body != null ? (String) body.get("estonian") : null;
        String english = body != null ? (String) body.get("english") : null;
        sentWordRepo.markWordSent(chatId, wordId, estonian, english);
        sentWordRepo.trackFeedShown(chatId, List.of(wordId));
        activityRepo.logWordActivity(chatId);
        return Map.of("seen", true, "wordId", wordId);
    }

    @PostMapping("/mastered/{wordId}")
    public ResponseEntity<?> markMastered(HttpServletRequest request, @PathVariable String wordId) {
        long chatId = getUserId(request);
        if (!sentWordRepo.canBeMastered(chatId, wordId)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "notReady",
                "message", "Quiz this word at least twice with good results before mastering"));
        }
        sentWordRepo.markWordMastered(chatId, wordId);
        return ResponseEntity.ok(Map.of("mastered", true, "wordId", wordId));
    }

    @DeleteMapping("/mastered/{wordId}")
    public Map<String, Object> unmarkMastered(HttpServletRequest request, @PathVariable String wordId) {
        sentWordRepo.unmarkWordMastered(getUserId(request), wordId);
        return Map.of("mastered", false, "wordId", wordId);
    }

    @PostMapping("/track-game-words")
    public Map<String, Object> trackGameWords(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        @SuppressWarnings("unchecked")
        List<String> words = (List<String>) body.get("words");
        String gameType = (String) body.getOrDefault("gameType", "unknown");

        if (words != null && !words.isEmpty()) {
            if ("crush".equals(gameType)) {
                sentWordRepo.trackCrushFound(chatId, words);
            } else {
                sentWordRepo.trackFeedShown(chatId, words);
            }
            for (String word : words) {
                sentWordRepo.updateSm2(chatId, word, 3);
            }
        }
        activityRepo.logGameActivity(chatId);
        return Map.of("ok", true, "tracked", words != null ? words.size() : 0);
    }


    @GetMapping("/weak-words")
    public Map<String, Object> getWeakWords(HttpServletRequest request,
                                             @RequestParam(defaultValue = "10") int limit) {
        long chatId = getUserId(request);
        List<Map<String, Object>> words = sentWordRepo.getWeakWords(chatId, Math.min(limit, 20));
        return Map.of("words", words, "total", words.size());
    }

    @GetMapping("/vocabulary")
    public Map<String, Object> getVocabulary(HttpServletRequest request) {
        long chatId = getUserId(request);
        List<Map<String, Object>> words = sentWordRepo.getVocabularyCollection(chatId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> w : words) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("wordId", w.get("word_id"));
            item.put("estonian", w.get("word_value"));
            item.put("english", w.get("english"));
            item.put("turkish", w.get("turkish"));
            item.put("cefrLevel", w.get("cefr_level"));
            item.put("feedCount", w.get("feed_count"));
            item.put("quizCount", w.get("quiz_count"));
            item.put("crushCount", w.get("crush_count"));
            item.put("mastered", w.get("mastered"));
            item.put("easeFactor", w.get("ease_factor"));
            item.put("nextReview", w.get("next_review"));
            item.put("firstSeen", w.get("sent_at"));
            item.put("lastSeen", w.get("last_fed_at"));
            item.put("lastQuizzed", w.get("last_quizzed_at"));
            items.add(item);
        }

        return Map.of("words", items, "total", items.size());
    }

}
