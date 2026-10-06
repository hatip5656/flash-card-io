package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(String type, String category, String title, String message, String metadataJson) {
        jdbc.update(
            "INSERT INTO admin_notifications (type, category, title, message, metadata) VALUES (?, ?, ?, ?, ?::jsonb)",
            type, category, title, message, metadataJson != null ? metadataJson : "{}"
        );
    }

    public List<Map<String, Object>> list(int limit, int offset, String category, Boolean unreadOnly) {
        var sb = new StringBuilder("SELECT * FROM admin_notifications WHERE 1=1");
        var params = new java.util.ArrayList<>();

        if (category != null && !category.isBlank()) {
            sb.append(" AND category = ?");
            params.add(category);
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            sb.append(" AND read = false");
        }

        sb.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.queryForList(sb.toString(), params.toArray());
    }

    public int countUnread() {
        var result = jdbc.queryForObject("SELECT COUNT(*) FROM admin_notifications WHERE read = false", Integer.class);
        return result != null ? result : 0;
    }

    public void markRead(long id) {
        jdbc.update("UPDATE admin_notifications SET read = true WHERE id = ?", id);
    }

    public void markAllRead() {
        jdbc.update("UPDATE admin_notifications SET read = true WHERE read = false");
    }

    public void deleteOlderThan(int days) {
        jdbc.update("DELETE FROM admin_notifications WHERE created_at < NOW() - INTERVAL '" + days + " days'");
    }
}
