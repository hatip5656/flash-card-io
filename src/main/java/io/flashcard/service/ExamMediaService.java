package io.flashcard.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Audio and pictures that exam questions and tasks reference by key, stored in exam_media. */
@Service
public class ExamMediaService {

    private static final Pattern KEY = Pattern.compile("[a-z0-9_-]+(/[a-z0-9_-]+)*\\.(mp3|jpg|jpeg|png|gif)");
    private static final Map<String, String> TYPES = Map.of(
        "mp3", "audio/mpeg", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif");
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    public record Media(String contentType, byte[] bytes) {}

    private final JdbcTemplate jdbc;

    public ExamMediaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A plain lowercase relative path with a known media extension. */
    static boolean validKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public static String contentType(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }

    /** The stored media for a key, or null when the key is invalid or nothing is stored under it. */
    public Media find(String key) {
        if (!validKey(key)) return null;
        var rows = jdbc.query("SELECT content_type, bytes FROM exam_media WHERE media_key = ?",
            (rs, i) -> new Media(rs.getString(1), rs.getBytes(2)), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean exists(String key) {
        if (!validKey(key)) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM exam_media WHERE media_key = ?", Integer.class, key);
        return n != null && n > 0;
    }

    public boolean store(String key, byte[] bytes) {
        if (!validKey(key) || bytes.length == 0 || bytes.length > MAX_BYTES) return false;
        jdbc.update("""
            INSERT INTO exam_media (media_key, content_type, bytes, size_bytes) VALUES (?, ?, ?, ?)
            ON CONFLICT (media_key) DO UPDATE SET content_type = EXCLUDED.content_type, bytes = EXCLUDED.bytes,
              size_bytes = EXCLUDED.size_bytes, created_at = NOW()
            """, key, contentType(key), bytes, bytes.length);
        return true;
    }
}
