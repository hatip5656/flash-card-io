package io.flashcard.controller;

import io.flashcard.service.CandidateDiscoveryService;
import io.flashcard.service.GrammarPodcastService;
import io.flashcard.service.SchedulerHistoryService;
import io.flashcard.service.WordAudioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/scheduler")
public class AdminSchedulerController {

    private final GrammarPodcastService grammarPodcastService;
    private final CandidateDiscoveryService candidateDiscoveryService;
    private final WordAudioService wordAudioService;
    private final SchedulerHistoryService historyService;

    public AdminSchedulerController(GrammarPodcastService grammarPodcastService,
                                    CandidateDiscoveryService candidateDiscoveryService,
                                    WordAudioService wordAudioService,
                                    SchedulerHistoryService historyService) {
        this.grammarPodcastService = grammarPodcastService;
        this.candidateDiscoveryService = candidateDiscoveryService;
        this.wordAudioService = wordAudioService;
        this.historyService = historyService;
    }

    // Grammar podcast scheduler
    @GetMapping("/grammar-podcast")
    public ResponseEntity<?> getGrammarPodcastStatus() {
        return ResponseEntity.ok(grammarPodcastService.getStatus());
    }

    @PostMapping("/grammar-podcast/trigger")
    public ResponseEntity<?> triggerGrammarPodcast() {
        Thread.startVirtualThread(() -> grammarPodcastService.generateNextGrammarPodcast());
        return ResponseEntity.ok(Map.of("triggered", true));
    }

    // Candidate discovery scheduler
    @GetMapping("/candidate-discovery")
    public ResponseEntity<?> getCandidateDiscoveryStatus() {
        return ResponseEntity.ok(candidateDiscoveryService.getStatus());
    }

    @PostMapping("/candidate-discovery/trigger")
    public ResponseEntity<?> triggerCandidateDiscovery() {
        Thread.startVirtualThread(() -> candidateDiscoveryService.discoverCandidates());
        return ResponseEntity.ok(Map.of("triggered", true));
    }

    // Word audio generation scheduler
    @GetMapping("/word-audio")
    public ResponseEntity<?> getWordAudioStatus() {
        return ResponseEntity.ok(wordAudioService.getStatus());
    }

    @PostMapping("/word-audio/trigger")
    public ResponseEntity<?> triggerWordAudio() {
        wordAudioService.triggerGeneration();
        return ResponseEntity.ok(Map.of("triggered", true));
    }

    // Scheduler run history
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(required = false) String job,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(Map.of("runs", historyService.getHistory(job, Math.min(limit, 200))));
    }
}
