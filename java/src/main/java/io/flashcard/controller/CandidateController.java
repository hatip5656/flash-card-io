package io.flashcard.controller;

import io.flashcard.repository.CandidateRepository;
import io.flashcard.service.WordBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/candidates")
public class CandidateController {

    private final CandidateRepository candidateRepo;
    private final WordBankService wordBankService;

    public CandidateController(CandidateRepository candidateRepo, WordBankService wordBankService) {
        this.candidateRepo = candidateRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping
    public Map<String, Object> listCandidates(@RequestParam(defaultValue = "pending") String status,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(defaultValue = "20") int limit) {
        return candidateRepo.listCandidates(status, level, Math.min(limit, 100));
    }

    @GetMapping("/stats")
    public Map<String, Object> candidateStats() {
        return candidateRepo.candidateStats();
    }

    @PatchMapping("/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> translateCandidate(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String turkish = (String) body.get("turkish");
        if (turkish == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "turkish translation required"));
        }
        List<Map<String, String>> sentences = (List<Map<String, String>>) body.get("sentences");
        Map<String, Object> result = candidateRepo.translateCandidate(id, turkish, sentences);
        if (result == null) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCandidate(@PathVariable int id) {
        try {
            String wordId = candidateRepo.approveCandidate(id);
            if (wordId == null) return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
            wordBankService.reload();
            return ResponseEntity.ok(Map.of("wordId", wordId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/approve-all")
    public Map<String, Object> approveAll(@RequestParam(required = false) String level) {
        Map<String, Integer> result = candidateRepo.approveAll(level);
        if (result.get("approved") > 0) wordBankService.reload();
        return Map.of("approved", result.get("approved"), "skipped", result.get("skipped"), "total", result.get("total"));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> rejectCandidate(@PathVariable int id) {
        candidateRepo.rejectCandidate(id);
        return Map.of("ok", true);
    }
}
