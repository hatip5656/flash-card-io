package io.flashcard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

@Service
public class DiskCacheService {

    private static final Logger log = LoggerFactory.getLogger(DiskCacheService.class);

    private final Path cacheDir;

    public static final long TTS_TTL_MS = 30L * 24 * 60 * 60 * 1000;
    public static final long UNSPLASH_TTL_MS = 90L * 24 * 60 * 60 * 1000;
    public static final long EKILEX_TTL_MS = 90L * 24 * 60 * 60 * 1000;

    public DiskCacheService() {
        String dir = System.getenv("CACHE_DIR");
        this.cacheDir = Path.of(dir != null ? dir : "./cache");
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    private String hashKey(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return input.hashCode() + "";
        }
    }

    private Path filePath(String namespace, String key, String ext) {
        return cacheDir.resolve(namespace).resolve(hashKey(key) + "." + ext);
    }

    public byte[] getCachedBuffer(String namespace, String key, String ext, long ttlMs) {
        Path path = filePath(namespace, key, ext);
        try {
            if (!Files.exists(path)) return null;
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (System.currentTimeMillis() - modified > ttlMs) return null;
            return Files.readAllBytes(path);
        } catch (Exception e) {
            return null;
        }
    }

    public void setCachedBuffer(String namespace, String key, String ext, byte[] data) {
        try {
            Path dir = cacheDir.resolve(namespace);
            Files.createDirectories(dir);
            Files.write(filePath(namespace, key, ext), data);
        } catch (Exception e) {
            log.error("[cache] Write error ({}): {}", namespace, e.getMessage());
        }
    }

    public String getCachedJson(String namespace, String key, long ttlMs) {
        Path path = filePath(namespace, key, "json");
        try {
            if (!Files.exists(path)) return null;
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (System.currentTimeMillis() - modified > ttlMs) return null;
            return Files.readString(path);
        } catch (Exception e) {
            return null;
        }
    }

    public void setCachedJson(String namespace, String key, String json) {
        try {
            Path dir = cacheDir.resolve(namespace);
            Files.createDirectories(dir);
            Files.writeString(filePath(namespace, key, "json"), json);
        } catch (Exception e) {
            log.error("[cache] Write error ({}): {}", namespace, e.getMessage());
        }
    }

    public int evictExpired(String namespace, long ttlMs) {
        int evicted = 0;
        Path dir = cacheDir.resolve(namespace);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> stream = Files.list(dir)) {
            long now = System.currentTimeMillis();
            for (Path path : stream.toList()) {
                try {
                    if (now - Files.getLastModifiedTime(path).toMillis() > ttlMs) {
                        Files.deleteIfExists(path);
                        evicted++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return evicted;
    }

    public Map<String, Map<String, Object>> getCacheStats() {
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        if (!Files.isDirectory(cacheDir)) return stats;
        try (Stream<Path> stream = Files.list(cacheDir)) {
            for (Path nsDir : stream.toList()) {
                if (!Files.isDirectory(nsDir)) continue;
                try (Stream<Path> files = Files.list(nsDir)) {
                    long totalSize = 0;
                    int count = 0;
                    for (Path f : files.toList()) {
                        try { totalSize += Files.size(f); count++; } catch (Exception ignored) {}
                    }
                    stats.put(nsDir.getFileName().toString(), Map.of("files", count, "sizeBytes", totalSize));
                }
            }
        } catch (Exception ignored) {}
        return stats;
    }
}
