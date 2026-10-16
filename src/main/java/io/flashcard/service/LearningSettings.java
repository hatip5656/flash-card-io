package io.flashcard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * The learning tuning values stored in learning_settings. There are no defaults in code: a missing key is
 * an error, because the database is where these values live. Cached for a minute so an admin change takes
 * effect without a restart.
 */
@Service
public class LearningSettings {

    private static final long TTL_MS = 60_000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile Map<String, JsonNode> cache;
    private volatile long loadedAt;

    public LearningSettings(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Fixed values, for tests. */
    public static LearningSettings of(Map<String, Object> values) {
        var mapper = new ObjectMapper();
        var s = new LearningSettings(null, mapper);
        Map<String, JsonNode> m = new HashMap<>();
        values.forEach((k, v) -> m.put(k, mapper.valueToTree(v)));
        s.cache = m;
        s.loadedAt = Long.MAX_VALUE;
        return s;
    }

    public double num(String key) {
        JsonNode v = get(key);
        if (!v.isNumber()) throw new IllegalStateException("learning setting " + key + " is not a number");
        return v.asDouble();
    }

    public int count(String key) {
        return (int) Math.round(num(key));
    }

    public List<String> list(String key) {
        JsonNode v = get(key);
        if (!v.isArray()) throw new IllegalStateException("learning setting " + key + " is not a list");
        List<String> out = new ArrayList<>();
        v.forEach(n -> out.add(n.asText()));
        return out;
    }

    private JsonNode get(String key) {
        JsonNode v = values().get(key);
        if (v == null) throw new IllegalStateException("learning setting " + key + " is missing from learning_settings");
        return v;
    }

    private Map<String, JsonNode> values() {
        if (cache == null || System.currentTimeMillis() - loadedAt > TTL_MS) reload();
        return cache;
    }

    public synchronized void reload() {
        if (jdbc == null) return;
        Map<String, JsonNode> m = new HashMap<>();
        for (var row : jdbc.queryForList("SELECT setting_key, value::text AS value FROM learning_settings")) {
            try {
                m.put((String) row.get("setting_key"), objectMapper.readTree((String) row.get("value")));
            } catch (Exception e) {
                throw new IllegalStateException("learning setting " + row.get("setting_key") + " is not valid JSON", e);
            }
        }
        cache = m;
        loadedAt = System.currentTimeMillis();
    }

    // ── Admin ──

    public List<Map<String, Object>> all() {
        return jdbc.queryForList(
            "SELECT setting_key, value::text AS value, description, updated_at FROM learning_settings ORDER BY setting_key");
    }

    /** Replaces a value; it must keep its type (number stays number, list stays list). False if unknown. */
    public boolean update(String key, Object value) throws Exception {
        var rows = jdbc.queryForList("SELECT value::text AS value FROM learning_settings WHERE setting_key = ?", key);
        if (rows.isEmpty()) return false;
        JsonNode current = objectMapper.readTree((String) rows.get(0).get("value"));
        JsonNode next = objectMapper.valueToTree(value);
        if (current.getNodeType() != next.getNodeType()) {
            throw new IllegalArgumentException(key + " must stay a " + current.getNodeType().name().toLowerCase());
        }
        jdbc.update("UPDATE learning_settings SET value = CAST(? AS JSONB), updated_at = NOW() WHERE setting_key = ?",
            objectMapper.writeValueAsString(next), key);
        reload();
        return true;
    }
}
