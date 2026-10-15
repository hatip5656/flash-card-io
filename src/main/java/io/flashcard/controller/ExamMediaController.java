package io.flashcard.controller;

import io.flashcard.service.ExamMediaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/** Serves exam media publicly (the app's audio player cannot send headers), and accepts uploads from admins. */
@RestController
public class ExamMediaController {

    private static final String PUBLIC_PREFIX = "/api/exam-media/";
    private static final String ADMIN_PREFIX = "/api/admin/exam-media/";

    private final ExamMediaService media;

    public ExamMediaController(ExamMediaService media) {
        this.media = media;
    }

    @GetMapping("/api/exam-media/**")
    public ResponseEntity<Resource> get(HttpServletRequest request) {
        var found = media.find(request.getRequestURI().substring(PUBLIC_PREFIX.length()));
        if (found == null) return ResponseEntity.notFound().build();
        // Range requests (seeking in audio) are handled by Spring for Resource bodies.
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(found.contentType()))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .body(new ByteArrayResource(found.bytes()));
    }

    /** Whether a key is stored, without sending the bytes (the importer skips what is already there). */
    @GetMapping("/api/admin/exam-media-exists/**")
    public Map<String, Object> exists(HttpServletRequest request) {
        String key = request.getRequestURI().substring("/api/admin/exam-media-exists/".length());
        return Map.of("key", key, "exists", media.exists(key));
    }

    @PutMapping("/api/admin/exam-media/**")
    public ResponseEntity<?> put(HttpServletRequest request, @RequestBody byte[] body) {
        String key = request.getRequestURI().substring(ADMIN_PREFIX.length());
        if (!media.store(key, body)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid media key or size"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "key", key, "bytes", body.length));
    }
}
