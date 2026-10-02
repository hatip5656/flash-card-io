package io.flashcard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.model.Podcast;
import io.flashcard.repository.PodcastRepository;
import io.flashcard.service.PodcastService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/podcast")
public class AdminPodcastController {

    private final PodcastService podcastService;
    private final PodcastRepository podcastRepo;
    private final ObjectMapper objectMapper;

    public AdminPodcastController(PodcastService podcastService, PodcastRepository podcastRepo,
                                  ObjectMapper objectMapper) {
        this.podcastService = podcastService;
        this.podcastRepo = podcastRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> listAll(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var items = podcastRepo.findAll(limit, offset);
        int total = podcastRepo.countAll();
        List<Map<String, Object>> summaries = items.stream().map(this::toSummary).toList();
        return ResponseEntity.ok(Map.of("items", summaries, "total", total));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable String id) {
        return podcastRepo.findById(id)
            .map(p -> ResponseEntity.ok().body(toSummary(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateFromScript(@RequestBody Map<String, Object> body) {
        String title = (String) body.getOrDefault("title", "Custom Lesson");
        String cefrLevel = (String) body.getOrDefault("cefrLevel", "A1");
        Object segmentsObj = body.get("segments");

        if (segmentsObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "segments array required"));
        }

        try {
            String segmentsJson = objectMapper.writeValueAsString(segmentsObj);
            String podcastId = podcastService.generateFromScript(title, cefrLevel, segmentsJson);
            return ResponseEntity.accepted().body(Map.of("id", podcastId, "status", "generating"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/assign")
    public ResponseEntity<?> assign(@RequestBody Map<String, Object> body) {
        String podcastId = (String) body.get("podcastId");
        if (podcastId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "podcastId required"));
        }

        var source = podcastRepo.findById(podcastId);
        if (source.isEmpty() || !"ready".equals(source.get().getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Podcast not found or not ready"));
        }

        Object chatIdsObj = body.get("chatIds");
        boolean assignAll = Boolean.TRUE.equals(body.get("all"));

        int count;
        if (assignAll) {
            count = podcastService.assignToAll(podcastId);
        } else if (chatIdsObj instanceof List<?> ids) {
            List<Long> chatIds = ids.stream()
                .map(id -> Long.parseLong(String.valueOf(id)))
                .toList();
            count = podcastService.assignToUsers(podcastId, chatIds);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "chatIds array or all=true required"));
        }

        return ResponseEntity.ok(Map.of("assigned", count));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        podcastRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @GetMapping("/sample")
    public ResponseEntity<?> getSample() {
        return ResponseEntity.ok(List.of(
            Map.of("text", "Tere! Tana me opime uusi sonu.", "language", "et", "pause_after_ms", 1000),
            Map.of("text", "Merhaba! Bugun yeni kelimeler ogrenecegiz.", "language", "tr", "pause_after_ms", 800),
            Map.of("text", "<<raamat>>", "language", "tr", "pause_after_ms", 2000),
            Map.of("text", "<<Raamat>> kitap demektir.", "language", "tr", "pause_after_ms", 800),
            Map.of("text", "Ma loen raamatut.", "language", "et", "pause_after_ms", 1500),
            Map.of("text", "Ben kitap okuyorum.", "language", "tr", "pause_after_ms", 1000)
        ));
    }

    private Map<String, Object> toSummary(Podcast p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("chatId", p.getChatId());
        map.put("title", p.getTitle());
        map.put("description", p.getDescription());
        map.put("status", p.getStatus());
        map.put("cefrLevel", p.getCefrLevel());
        map.put("durationSeconds", p.getDurationSeconds());
        map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        if ("ready".equals(p.getStatus()) && p.getAudioCacheKey() != null) {
            map.put("audioUrl", "https://wordagram.hatip.dev/podcasts/" + p.getAudioCacheKey());
        }
        if ("ready".equals(p.getStatus()) && p.getScript() != null) {
            try {
                map.put("subtitles", objectMapper.readValue(p.getScript(), List.class));
            } catch (Exception ignored) {}
        }
        if (p.getErrorMessage() != null) {
            map.put("errorMessage", p.getErrorMessage());
        }
        return map;
    }
}
