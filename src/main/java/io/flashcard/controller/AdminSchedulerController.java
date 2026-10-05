package io.flashcard.controller;

import io.flashcard.service.CandidateDiscoveryService;
import io.flashcard.service.GrammarPodcastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/scheduler")
public class AdminSchedulerController {

    private final GrammarPodcastService grammarPodcastService;
    private final CandidateDiscoveryService candidateDiscoveryService;

    public AdminSchedulerController(GrammarPodcastService grammarPodcastService,
                                    CandidateDiscoveryService candidateDiscoveryService) {
        this.grammarPodcastService = grammarPodcastService;
        this.candidateDiscoveryService = candidateDiscoveryService;
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
}
