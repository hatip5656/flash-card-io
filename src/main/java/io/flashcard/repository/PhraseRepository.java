package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class PhraseRepository {

    private final JdbcTemplate jdbc;

    public PhraseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getPhrasesForUser(long chatId, String userLevel, int limit) {
        List<String> levelOrder = List.of("A1", "A2", "B1", "B2");
        int maxIndex = levelOrder.indexOf(userLevel);
        if (maxIndex < 0) maxIndex = 0;
        List<String> allowedLevels = levelOrder.subList(0, maxIndex + 1);

        String placeholders = String.join(",", Collections.nCopies(allowedLevels.size(), "?"));

        String sql = """
            SELECT p.id, p.estonian, p.english, p.turkish, p.category, p.cefr_level, p.context_note,
                   sp.seen_at
            FROM feed_phrases p
            LEFT JOIN sent_phrases sp ON sp.phrase_id = p.id AND sp.chat_id = ?
            WHERE p.cefr_level IN (%s)
            ORDER BY sp.seen_at ASC NULLS FIRST, p.sort_order
            LIMIT ?
            """.formatted(placeholders);

        List<Object> params = new ArrayList<>();
        params.add(chatId);
        params.addAll(allowedLevels);
        params.add(limit);

        return jdbc.queryForList(sql, params.toArray());
    }

    public void markSeen(long chatId, int phraseId) {
        jdbc.update("""
            INSERT INTO sent_phrases (chat_id, phrase_id) VALUES (?, ?)
            ON CONFLICT (chat_id, phrase_id) DO UPDATE SET seen_at = NOW()
            """, chatId, phraseId);
    }
}
