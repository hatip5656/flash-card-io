package io.flashcard.controller;

import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cache")
public class CacheController {

    private final CacheManager cacheManager;

    public CacheController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostMapping("/evict-all")
    public ResponseEntity<Map<String, Object>> evictAll() {
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
    public ResponseEntity<Map<String, Object>> evictCache(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Cache not found: " + cacheName));
        }
        cache.clear();
        return ResponseEntity.ok(Map.of("evicted", cacheName));
    }

    @GetMapping("/names")
    public ResponseEntity<Map<String, Object>> listCaches() {
        return ResponseEntity.ok(Map.of("caches", cacheManager.getCacheNames()));
    }
}
