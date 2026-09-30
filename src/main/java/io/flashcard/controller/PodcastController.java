package io.flashcard.controller;

import io.flashcard.model.Podcast;
import io.flashcard.service.PodcastService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/podcast")
public class PodcastController {

    private final PodcastService podcastService;

    public PodcastController(PodcastService podcastService) {
        this.podcastService = podcastService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(HttpServletRequest request) {
        long chatId = getUserId(request);
        try {
            String podcastId = podcastService.requestGeneration(chatId);
            return ResponseEntity.accepted().body(Map.of(
                "id", podcastId,
                "status", "pending"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        return podcastService.getStatus(id)
            .map(p -> ResponseEntity.ok().body(toSummary(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<?> getAudio(@PathVariable String id) {
        byte[] audio = podcastService.getAudio(id);
        if (audio == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "audio/wav");
        headers.setCacheControl("public, max-age=86400");
        headers.setContentLength(audio.length);
        return new ResponseEntity<>(audio, headers, HttpStatus.OK);
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatest(HttpServletRequest request) {
        long chatId = getUserId(request);
        return podcastService.getLatest(chatId)
            .map(p -> ResponseEntity.ok().body(toSummary(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(HttpServletRequest request,
                                        @RequestParam(defaultValue = "5") int limit) {
        long chatId = getUserId(request);
        List<Map<String, Object>> items = podcastService.getHistory(chatId, limit).stream()
            .map(this::toSummary)
            .toList();
        return ResponseEntity.ok(items);
    }

    private Map<String, Object> toSummary(Podcast p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("title", p.getTitle());
        map.put("description", p.getDescription());
        map.put("status", p.getStatus());
        map.put("cefrLevel", p.getCefrLevel());
        map.put("durationSeconds", p.getDurationSeconds());
        map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        if (p.getErrorMessage() != null) {
            map.put("errorMessage", p.getErrorMessage());
        }
        return map;
    }
}
