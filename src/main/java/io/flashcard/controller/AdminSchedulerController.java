package io.flashcard.controller;

import io.flashcard.service.GrammarPodcastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/scheduler")
public class AdminSchedulerController {

    private final GrammarPodcastService grammarPodcastService;

    public AdminSchedulerController(GrammarPodcastService grammarPodcastService) {
        this.grammarPodcastService = grammarPodcastService;
    }

    @GetMapping("/grammar-podcast")
    public ResponseEntity<?> getGrammarPodcastStatus() {
        return ResponseEntity.ok(grammarPodcastService.getStatus());
    }

    @PostMapping("/grammar-podcast/trigger")
    public ResponseEntity<?> triggerGrammarPodcast() {
        Thread.startVirtualThread(() -> grammarPodcastService.generateNextGrammarPodcast());
        return ResponseEntity.ok(Map.of("triggered", true));
    }

    @PostMapping("/grammar-podcast/trigger/{lessonId}")
    public ResponseEntity<?> triggerSpecificLesson(@PathVariable String lessonId) {
        grammarPodcastService.triggerManual(lessonId);
        return ResponseEntity.ok(Map.of("queued", lessonId));
    }
}
