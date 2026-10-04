package io.flashcard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AccountLinkService {

    private static final Logger log = LoggerFactory.getLogger(AccountLinkService.class);
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MINUTES = 5;

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public AccountLinkService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Generate a 6-digit OTP code for a mobile user to link their Telegram account.
     */
    public String generateLinkCode(long mobileUserId) {
        // Invalidate any existing unused codes for this user
        jdbc.update("DELETE FROM link_codes WHERE chat_id = ? AND used = FALSE", mobileUserId);

        String code = String.format("%06d", random.nextInt(1000000));
        Instant expiresAt = Instant.now().plusSeconds(CODE_EXPIRY_MINUTES * 60);

        jdbc.update("INSERT INTO link_codes (code, chat_id, expires_at) VALUES (?, ?, ?)",
            code, mobileUserId, java.sql.Timestamp.from(expiresAt));

        log.info("[link] Generated code {} for mobile user {}", code, mobileUserId);
        return code;
    }

    /**
     * Validate a link code from Telegram and create the account link.
     * Returns the mobile user ID if successful, empty otherwise.
     */
    @Transactional
    public Optional<Long> connectTelegram(long telegramChatId, String code) {
        // Find valid, unused, non-expired code
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT chat_id FROM link_codes
            WHERE code = ? AND used = FALSE AND expires_at > NOW()
            """, code);

        if (rows.isEmpty()) {
            log.warn("[link] Invalid or expired code: {}", code);
            return Optional.empty();
        }

        long mobileId = ((Number) rows.get(0).get("chat_id")).longValue();

        // Check if this telegram account is already linked
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM linked_accounts WHERE telegram_id = ?",
            Integer.class, telegramChatId);
        if (existing != null && existing > 0) {
            log.warn("[link] Telegram {} already linked", telegramChatId);
            return Optional.empty();
        }

        // Mark code as used
        jdbc.update("UPDATE link_codes SET used = TRUE WHERE code = ?", code);

        // Create the link
        jdbc.update("INSERT INTO linked_accounts (mobile_id, telegram_id) VALUES (?, ?)",
            mobileId, telegramChatId);

        log.info("[link] Linked telegram {} to mobile {}", telegramChatId, mobileId);

        // Merge data from telegram account into mobile account
        mergeAccounts(mobileId, telegramChatId);

        return Optional.of(mobileId);
    }

    /**
     * Merge telegram user's learning data into the mobile account.
     * Handles duplicate words by keeping the harder (more-practiced) entry.
     */
    @Transactional
    public void mergeAccounts(long mobileId, long telegramId) {
        log.info("[link] Merging data from telegram {} into mobile {}", telegramId, mobileId);

        // Merge sent_words: for duplicates, keep entry with more quiz data
        // First: insert words that only exist in telegram
        int newWords = jdbc.update("""
            INSERT INTO sent_words (chat_id, word_id, word_value, english, sent_at,
                quiz_count, ease_factor, interval_days, next_review, repetitions,
                seen_count, crush_count, last_fed_at, last_quizzed_at, last_crushed_at,
                mastered, mastered_at)
            SELECT ?, word_id, word_value, english, sent_at,
                quiz_count, ease_factor, interval_days, next_review, repetitions,
                seen_count, crush_count, last_fed_at, last_quizzed_at, last_crushed_at,
                mastered, mastered_at
            FROM sent_words WHERE chat_id = ?
            AND word_id NOT IN (SELECT word_id FROM sent_words WHERE chat_id = ?)
            """, mobileId, telegramId, mobileId);

        // For duplicates: merge counts, keep lower ease_factor (harder = needs more practice)
        int mergedWords = jdbc.update("""
            UPDATE sent_words m SET
                seen_count = m.seen_count + t.seen_count,
                quiz_count = m.quiz_count + t.quiz_count,
                crush_count = m.crush_count + t.crush_count,
                ease_factor = LEAST(m.ease_factor, t.ease_factor),
                sent_at = LEAST(m.sent_at, t.sent_at),
                last_quizzed_at = GREATEST(m.last_quizzed_at, t.last_quizzed_at)
            FROM sent_words t
            WHERE m.chat_id = ? AND t.chat_id = ? AND m.word_id = t.word_id
            """, mobileId, telegramId);

        // Merge activity_log: sum daily values
        jdbc.update("""
            INSERT INTO activity_log (chat_id, activity_date, words_learned, quizzes_taken,
                games_played, phrases_seen, stories_read, dialogs_completed)
            SELECT ?, activity_date, words_learned, quizzes_taken,
                games_played, phrases_seen, stories_read, dialogs_completed
            FROM activity_log WHERE chat_id = ?
            ON CONFLICT (chat_id, activity_date) DO UPDATE SET
                words_learned = activity_log.words_learned + EXCLUDED.words_learned,
                quizzes_taken = activity_log.quizzes_taken + EXCLUDED.quizzes_taken,
                games_played = activity_log.games_played + EXCLUDED.games_played
            """, mobileId, telegramId);

        // Copy saved_words that don't exist in mobile
        jdbc.update("""
            INSERT INTO saved_words (chat_id, word_id, saved_at)
            SELECT ?, word_id, saved_at FROM saved_words WHERE chat_id = ?
            AND word_id NOT IN (SELECT word_id FROM saved_words WHERE chat_id = ?)
            """, mobileId, telegramId, mobileId);

        // Copy sent_grammar that don't exist in mobile
        jdbc.update("""
            INSERT INTO sent_grammar (chat_id, lesson_id, sent_at)
            SELECT ?, lesson_id, sent_at FROM sent_grammar WHERE chat_id = ?
            AND lesson_id NOT IN (SELECT lesson_id FROM sent_grammar WHERE chat_id = ?)
            """, mobileId, telegramId, mobileId);

        // Clean up telegram's data (now merged)
        jdbc.update("DELETE FROM sent_words WHERE chat_id = ?", telegramId);
        jdbc.update("DELETE FROM activity_log WHERE chat_id = ?", telegramId);
        jdbc.update("DELETE FROM saved_words WHERE chat_id = ?", telegramId);
        jdbc.update("DELETE FROM sent_grammar WHERE chat_id = ?", telegramId);

        log.info("[link] Merge complete: {} new words, {} merged words", newWords, mergedWords);
    }

    /**
     * Get the linked mobile account ID for a telegram user.
     * Returns the telegram's own chatId if not linked.
     */
    public long getEffectiveChatId(long telegramChatId) {
        List<Long> linked = jdbc.queryForList(
            "SELECT mobile_id FROM linked_accounts WHERE telegram_id = ?",
            Long.class, telegramChatId);
        return linked.isEmpty() ? telegramChatId : linked.get(0);
    }

    /**
     * Check if a mobile user has a linked telegram account.
     */
    public Optional<Long> getLinkedTelegramId(long mobileId) {
        List<Long> linked = jdbc.queryForList(
            "SELECT telegram_id FROM linked_accounts WHERE mobile_id = ?",
            Long.class, mobileId);
        return linked.isEmpty() ? Optional.empty() : Optional.of(linked.get(0));
    }

    /**
     * Get link status for a mobile user (for API response).
     */
    public Map<String, Object> getLinkStatus(long mobileId) {
        var telegram = getLinkedTelegramId(mobileId);
        if (telegram.isPresent()) {
            // Get telegram username
            List<String> usernames = jdbc.queryForList(
                "SELECT username FROM subscribers WHERE chat_id = ?",
                String.class, telegram.get());
            return Map.of(
                "linked", true,
                "telegramId", telegram.get(),
                "telegramUsername", usernames.isEmpty() ? "" : (usernames.get(0) != null ? usernames.get(0) : "")
            );
        }
        return Map.of("linked", false);
    }

    /**
     * Unlink accounts.
     */
    public void unlink(long mobileId) {
        jdbc.update("DELETE FROM linked_accounts WHERE mobile_id = ?", mobileId);
        log.info("[link] Unlinked mobile {}", mobileId);
    }
}
