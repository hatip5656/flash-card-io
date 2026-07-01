package io.flashcard.controller;

import io.flashcard.model.GrammarStory;
import io.flashcard.repository.StoryReadRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.StoryBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryBankService storyBankService;
    private final StoryReadRepository storyReadRepo;
    private final SubscriberRepository subscriberRepo;

    public StoryController(StoryBankService storyBankService, StoryReadRepository storyReadRepo,
                           SubscriberRepository subscriberRepo) {
        this.storyBankService = storyBankService;
        this.storyReadRepo = storyReadRepo;
        this.subscriberRepo = subscriberRepo;
    }

    @GetMapping
    public List<Map<String, Object>> getStories(HttpServletRequest request) {
        long chatId = getUserId(request);
        String level = subscriberRepo.getSubscriberLevel(chatId);
        Set<String> readIds = storyReadRepo.getReadStoryIds(chatId);
        List<GrammarStory> stories = storyBankService.getStoriesForLevel(level);

        List<Map<String, Object>> result = stories.stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("topic", s.getTopic());
            map.put("icon", s.getIcon());
            map.put("cefrLevel", s.getCefrLevel());
            map.put("slideCount", s.getSlides().size());
            map.put("isRead", readIds.contains(s.getId()));
            map.put("slides", s.getSlides());
            return map;
        }).collect(Collectors.toCollection(ArrayList::new));

        result.sort((a, b) -> {
            boolean aRead = (boolean) a.get("isRead");
            boolean bRead = (boolean) b.get("isRead");
            if (aRead != bRead) return aRead ? 1 : -1;
            return 0;
        });

        return result;
    }

    @PostMapping("/{storyId}/read")
    public Map<String, Object> markStoryAsRead(HttpServletRequest request, @PathVariable String storyId) {
        storyReadRepo.markStoryRead(getUserId(request), storyId);
        return Map.of("storyId", storyId, "read", true);
    }
}
