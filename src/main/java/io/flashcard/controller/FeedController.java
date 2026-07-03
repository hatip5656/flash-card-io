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
        List<Word> unsent = wordBankService.getUnsent(level, sentIds);

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

            // If no cached image, try to fetch dynamically
            if (img == null) {
                String query = word.getImageQuery() != null ? word.getImageQuery() : word.getEnglish();
                ImageService.ImageResult fetched = imageService.fetchImage(query);
                if (fetched != null) {
                    img = Map.of("url", fetched.url(), "photographer", fetched.photographer());
                    wordDbRepo.updateImageCache(word.getId(), fetched.url(), fetched.photographer());
                }
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

        // Track feed views
        List<String> shownWordIds = items.stream()
            .filter(i -> !(boolean) i.get("isNew"))
            .map(i -> (String) ((Map<?, ?>) i.get("word")).get("id"))
            .toList();
        if (!shownWordIds.isEmpty()) {
            sentWordRepo.trackFeedShown(chatId, shownWordIds);
        }

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
        activityRepo.logWordActivity(chatId);
        return Map.of("seen", true, "wordId", wordId);
    }

    @PostMapping("/mastered/{wordId}")
    public Map<String, Object> markMastered(HttpServletRequest request, @PathVariable String wordId) {
        sentWordRepo.markWordMastered(getUserId(request), wordId);
        return Map.of("mastered", true, "wordId", wordId);
    }

    @DeleteMapping("/mastered/{wordId}")
    public Map<String, Object> unmarkMastered(HttpServletRequest request, @PathVariable String wordId) {
        sentWordRepo.unmarkWordMastered(getUserId(request), wordId);
        return Map.of("mastered", false, "wordId", wordId);
    }
}
