package io.flashcard.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Audio and pictures that exam questions reference by key (e.g. "testest/mm_823/a2kuulamine.mp3"). */
@Service
public class ExamMediaService {

    private static final Pattern KEY = Pattern.compile("[a-z0-9_-]+(/[a-z0-9_-]+)*\\.(mp3|jpg|jpeg|png|gif)");
    private static final Map<String, String> TYPES = Map.of(
        "mp3", "audio/mpeg", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif");
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final Path root;

    public ExamMediaService(DiskCacheService diskCache) {
        this.root = diskCache.getCacheDir().resolve("exam-media");
    }

    /** Null unless the key is a plain lowercase relative path with a known media extension. */
    Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) return null;
        Path p = root.resolve(key).normalize();
        return p.startsWith(root) ? p : null;
    }

    public static String contentType(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }

    /** The file for a key, or null when the key is invalid or nothing is stored under it. */
    public Path find(String key) {
        Path p = resolve(key);
        return p != null && Files.isRegularFile(p) ? p : null;
    }

    public boolean store(String key, byte[] bytes) throws IOException {
        Path p = resolve(key);
        if (p == null || bytes.length == 0 || bytes.length > MAX_BYTES) return false;
        Files.createDirectories(p.getParent());
        Path tmp = p.resolveSibling(p.getFileName() + ".part");
        Files.write(tmp, bytes);
        Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        return true;
    }
}
