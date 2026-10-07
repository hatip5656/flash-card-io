package io.flashcard.controller;

import io.flashcard.repository.CandidateRepository;
import io.flashcard.service.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/candidates")
public class CandidateController {

    private final CandidateRepository candidateRepo;
    private final CandidateService candidateService;

    public CandidateController(CandidateRepository candidateRepo, CandidateService candidateService) {
        this.candidateRepo = candidateRepo;
        this.candidateService = candidateService;
    }

    @GetMapping
    public Map<String, Object> listCandidates(@RequestParam(defaultValue = "pending") String status,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "20") int limit,
                                               @RequestParam(defaultValue = "0") int offset) {
        return candidateRepo.listCandidates(status, level, q, Math.min(limit, 100), offset);
    }

    @GetMapping("/stats")
    public Map<String, Object> candidateStats() {
        return candidateRepo.candidateStats();
    }

    @PatchMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> translateCandidate(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String turkish = (String) body.get("turkish");
        if (turkish == null) return ResponseEntity.badRequest().body(Map.of("error", "turkish translation required"));
        List<Map<String, String>> sentences = (List<Map<String, String>>) body.get("sentences");
        Map<String, Object> result = candidateRepo.translateCandidate(id, turkish, sentences);
        if (result == null) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/auto-translate")
    public ResponseEntity<?> autoTranslate(@PathVariable int id) {
        return ResponseEntity.ok(candidateService.autoTranslate(id));
    }

    @PostMapping("/{id}/sentences")
    public ResponseEntity<?> addSentence(@PathVariable int id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(candidateService.addSentence(id, body.get("estonian"), body.get("english"), body.get("turkish")));
    }

    @PatchMapping("/{id}/sentences/{sentenceId}")
    public ResponseEntity<?> updateSentence(@PathVariable int id, @PathVariable int sentenceId,
                                             @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(candidateService.updateSentence(id, sentenceId,
            body.getOrDefault("estonian", null), body.getOrDefault("english", null), body.getOrDefault("turkish", null)));
    }

    @DeleteMapping("/{id}/sentences/{sentenceId}")
    public ResponseEntity<?> deleteSentence(@PathVariable int id, @PathVariable int sentenceId) {
        return ResponseEntity.ok(Map.of("deleted", candidateService.deleteSentence(id, sentenceId)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCandidate(@PathVariable int id) {
        String wordId = candidateService.approve(id);
        if (wordId == null) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        return ResponseEntity.ok(Map.of("wordId", wordId));
    }

    @PostMapping("/approve-all")
    public Map<String, Object> approveAll(@RequestParam(required = false) String level) {
        return candidateService.approveAll(level);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/approve-batch")
    public Map<String, Object> approveBatch(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        if (ids == null || ids.isEmpty()) return Map.of("approved", 0, "failed", 0);
        return candidateService.approveBatch(ids.stream().map(Number::intValue).toList());
    }

    @PostMapping("/translate-all")
    public Map<String, Object> translateAll() {
        candidateService.translateAllAsync();
        return Map.of("started", true, "message", "Translation running in background. You'll get a notification when done.");
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> rejectCandidate(@PathVariable int id) {
        candidateRepo.rejectCandidate(id);
        return Map.of("ok", true);
    }
}
