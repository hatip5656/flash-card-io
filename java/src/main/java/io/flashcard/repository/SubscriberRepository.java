package io.flashcard.repository;

import io.flashcard.model.Subscriber;
import io.flashcard.model.UserPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class SubscriberRepository {

    private static final String DEFAULT_SCHEDULE = "0 9 * * *";
    private static final String SCHEDULE_OFF = "off";
    private static final long MOBILE_ID_BASE = 2_000_000_000L;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SubscriberRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void addSubscriber(long chatId, String channel, String username, String firstName) {
        jdbc.update("""
            INSERT INTO subscribers (chat_id, channel, username, first_name)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (chat_id) DO UPDATE SET
              active = TRUE,
              channel = EXCLUDED.channel,
              username = COALESCE(EXCLUDED.username, subscribers.username),
              first_name = COALESCE(EXCLUDED.first_name, subscribers.first_name)
            """,
            chatId, channel, username, firstName);
    }

    public void removeSubscriber(long chatId) {
        jdbc.update("UPDATE subscribers SET active = FALSE WHERE chat_id = ?", chatId);
    }

    public List<Subscriber> getActiveSubscribers() {
        return jdbc.query(
            "SELECT chat_id, channel, cefr_level, schedule, active FROM subscribers WHERE active = TRUE",
            this::mapSubscriber);
    }

    public String getSubscriberLevel(long chatId) {
        List<String> levels = jdbc.queryForList(
            "SELECT cefr_level FROM subscribers WHERE chat_id = ?", String.class, chatId);
        return levels.isEmpty() ? "A1" : levels.get(0);
    }

    public void setSubscriberLevel(long chatId, String level) {
        jdbc.update("UPDATE subscribers SET cefr_level = ? WHERE chat_id = ?", level, chatId);
    }

    public String getSubscriberSchedule(long chatId) {
        List<String> schedules = jdbc.queryForList(
            "SELECT schedule FROM subscribers WHERE chat_id = ?", String.class, chatId);
        return schedules.isEmpty() ? DEFAULT_SCHEDULE : schedules.get(0);
    }

    public void setSubscriberSchedule(long chatId, String schedule) {
        jdbc.update("UPDATE subscribers SET schedule = ? WHERE chat_id = ?", schedule, chatId);
    }

    public UserPreferences getPreferences(long chatId) {
        List<String> rows = jdbc.queryForList(
            "SELECT preferences FROM subscribers WHERE chat_id = ?", String.class, chatId);
        UserPreferences defaults = UserPreferences.defaults();
        if (rows.isEmpty()) return defaults;
        try {
            Map<String, Object> stored = objectMapper.readValue(rows.get(0), new TypeReference<>() {});
            return mergePreferences(defaults, stored);
        } catch (Exception e) {
            return defaults;
        }
    }

    public void updatePreference(long chatId, String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(key, value));
            jdbc.update(
                "UPDATE subscribers SET preferences = preferences || ?::jsonb WHERE chat_id = ?",
                json, chatId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize preference", e);
        }
    }

    public long getNextMobileUserId() {
        Long nextId = jdbc.queryForObject(
            "SELECT COALESCE(MAX(chat_id), ? - 1) + 1 FROM subscribers WHERE chat_id >= ?",
            Long.class, MOBILE_ID_BASE, MOBILE_ID_BASE);
        return nextId != null ? nextId : MOBILE_ID_BASE;
    }

    public void updateNextDelivery(long chatId, Instant nextDeliveryAt) {
        jdbc.update("UPDATE subscribers SET next_delivery_at = ? WHERE chat_id = ?",
            nextDeliveryAt != null ? java.sql.Timestamp.from(nextDeliveryAt) : null, chatId);
    }

    public void updateNextGrammar(long chatId, Instant nextGrammarAt) {
        jdbc.update("UPDATE subscribers SET next_grammar_at = ? WHERE chat_id = ?",
            nextGrammarAt != null ? java.sql.Timestamp.from(nextGrammarAt) : null, chatId);
    }

    public List<Long> getUsersDueForDelivery() {
        return jdbc.queryForList(
            "SELECT chat_id FROM subscribers WHERE active = TRUE AND schedule != ? AND next_delivery_at IS NOT NULL AND next_delivery_at <= NOW()",
            Long.class, SCHEDULE_OFF);
    }

    public List<Long> getUsersDueForGrammar() {
        return jdbc.queryForList(
            "SELECT chat_id FROM subscribers WHERE active = TRUE AND next_grammar_at IS NOT NULL AND next_grammar_at <= NOW()",
            Long.class);
    }

    public int initNextDeliveryForAll(List<long[]> updates) {
        int count = 0;
        for (long[] pair : updates) {
            jdbc.update("UPDATE subscribers SET next_delivery_at = to_timestamp(?) WHERE chat_id = ?",
                pair[1] / 1000.0, pair[0]);
            count++;
        }
        return count;
    }

    public List<Map<String, Object>> getSubscribersNeedingDeliveryInit() {
        return jdbc.queryForList(
            "SELECT chat_id, schedule FROM subscribers WHERE active = TRUE AND next_delivery_at IS NULL AND schedule != ?",
            SCHEDULE_OFF);
    }

    public List<Long> getSubscribersNeedingGrammarInit() {
        return jdbc.queryForList(
            "SELECT chat_id FROM subscribers WHERE active = TRUE AND next_grammar_at IS NULL",
            Long.class);
    }

    public String getFirstName(long chatId) {
        List<String> names = jdbc.queryForList(
            "SELECT first_name FROM subscribers WHERE chat_id = ?", String.class, chatId);
        return names.isEmpty() ? null : names.get(0);
    }

    private Subscriber mapSubscriber(ResultSet rs, int rowNum) throws SQLException {
        Subscriber s = new Subscriber();
        s.setChatId(rs.getLong("chat_id"));
        s.setChannel(rs.getString("channel"));
        s.setCefrLevel(rs.getString("cefr_level"));
        String schedule = rs.getString("schedule");
        s.setSchedule(schedule != null ? schedule : DEFAULT_SCHEDULE);
        s.setActive(rs.getBoolean("active"));
        return s;
    }

    private UserPreferences mergePreferences(UserPreferences defaults, Map<String, Object> stored) {
        if (stored.containsKey("audio")) defaults.setAudio((Boolean) stored.get("audio"));
        if (stored.containsKey("voiceName")) defaults.setVoiceName((String) stored.get("voiceName"));
        if (stored.containsKey("wordForms")) defaults.setWordForms((Boolean) stored.get("wordForms"));
        if (stored.containsKey("grammarCards")) defaults.setGrammarCards((Boolean) stored.get("grammarCards"));
        if (stored.containsKey("dailySummary")) defaults.setDailySummary((Boolean) stored.get("dailySummary"));
        if (stored.containsKey("weeklyReport")) defaults.setWeeklyReport((Boolean) stored.get("weeklyReport"));
        if (stored.containsKey("nativeLanguage")) defaults.setNativeLanguage((String) stored.get("nativeLanguage"));
        if (stored.containsKey("theme")) defaults.setTheme((String) stored.get("theme"));
        return defaults;
    }
}
