package io.flashcard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cache")
public class CacheController {

    @Value("${admin.cache-secret:HatipIsTheBoss}")
    private String secret;

    private final CacheManager cacheManager;

    public CacheController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostMapping("/evict-all")
    public ResponseEntity<Map<String, Object>> evictAll(@RequestHeader("X-Cache-Secret") String secretHeader) {
        if (!this.secret.equals(secretHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid secret"));
        }

        int evicted = 0;
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
                evicted++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "evicted", evicted,
            "caches", cacheManager.getCacheNames()));
    }

    @PostMapping("/evict/{cacheName}")
    public ResponseEntity<Map<String, Object>> evictCache(
            @RequestHeader("X-Cache-Secret") String secretHeader,
            @PathVariable String cacheName) {
        if (!this.secret.equals(secretHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid secret"));
        }

        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Cache not found: " + cacheName));
        }

        cache.clear();
        return ResponseEntity.ok(Map.of("evicted", cacheName));
    }

    @GetMapping("/names")
    public ResponseEntity<Map<String, Object>> listCaches(@RequestHeader("X-Cache-Secret") String secretHeader) {
        if (!this.secret.equals(secretHeader)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid secret"));
        }

        return ResponseEntity.ok(Map.of("caches", cacheManager.getCacheNames()));
    }
}
