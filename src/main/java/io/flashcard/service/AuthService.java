package io.flashcard.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** App sessions and the sign-in accounts (Google) attached to learners. */
@Service
public class AuthService {

    public static final String GOOGLE = "google";

    /** The account is already attached to a different learner. */
    public static class IdentityTakenException extends Exception {
        public IdentityTakenException() { super("This Google account is already connected to another Wordagram account"); }
    }

    /** The learner already has a different account of this provider. */
    public static class AlreadyLinkedException extends Exception {
        public AlreadyLinkedException() { super("This Wordagram account is already connected to a different Google account"); }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    // A session not used for this long has to sign in again
    private static final int SESSION_IDLE_DAYS = 90;

    private final JdbcTemplate jdbc;
    // Every authenticated request resolves its token; keep recent ones in memory
    private final Cache<String, Long> sessions = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .build();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Sessions ──────────────────────────────────

    public String createSession(long chatId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO auth_sessions (token_hash, chat_id) VALUES (?, ?)", hash(token), chatId);
        return token;
    }

    /** The learner a session token belongs to, or empty when it is unknown or revoked. */
    public Optional<Long> resolveSession(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String key = hash(token);
        Long cached = sessions.getIfPresent(key);
        if (cached != null) return Optional.of(cached);
        List<Long> rows = jdbc.queryForList("""
            UPDATE auth_sessions SET last_used_at = NOW()
            WHERE token_hash = ? AND last_used_at > NOW() - make_interval(days => ?)
            RETURNING chat_id
            """, Long.class, key, SESSION_IDLE_DAYS);
        if (rows.isEmpty()) return Optional.empty();
        sessions.put(key, rows.get(0));
        return Optional.of(rows.get(0));
    }

    public void revokeSession(String token) {
        if (token == null || token.isBlank()) return;
        String key = hash(token);
        jdbc.update("DELETE FROM auth_sessions WHERE token_hash = ?", key);
        sessions.invalidate(key);
    }

    public boolean hasSessions(long chatId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE chat_id = ?", Integer.class, chatId);
        return n != null && n > 0;
    }

    /** Remove a deleted learner's sign-in accounts and sessions. */
    public void forgetLearner(long chatId) {
        jdbc.update("DELETE FROM user_identities WHERE chat_id = ?", chatId);
        jdbc.update("DELETE FROM auth_sessions WHERE chat_id = ?", chatId);
        sessions.invalidateAll();
    }

    // ── Identities ────────────────────────────────

    public Optional<Long> findLearner(String provider, String subject) {
        List<Long> rows = jdbc.queryForList(
            "SELECT chat_id FROM user_identities WHERE provider = ? AND subject = ?", Long.class, provider, subject);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void recordLogin(String provider, String subject, String email, String name) {
        jdbc.update("""
            UPDATE user_identities SET last_login_at = NOW(),
                email = COALESCE(?, email), name = COALESCE(?, name)
            WHERE provider = ? AND subject = ?
            """, email, name, provider, subject);
    }

    /** Attach an account to a learner; signing in again with the same account is a no-op. */
    public void link(long chatId, String provider, String subject, String email, String name)
            throws IdentityTakenException, AlreadyLinkedException {
        Optional<Long> owner = findLearner(provider, subject);
        if (owner.isPresent()) {
            if (owner.get() != chatId) throw new IdentityTakenException();
            recordLogin(provider, subject, email, name);
            return;
        }
        try {
            jdbc.update("""
                INSERT INTO user_identities (provider, subject, chat_id, email, name, last_login_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                """, provider, subject, chatId, email, name);
        } catch (DuplicateKeyException e) {
            // Either the learner has another account of this provider, or a concurrent link won the race
            if (findLearner(provider, subject).filter(id -> id != chatId).isPresent()) throw new IdentityTakenException();
            if (findLearner(provider, subject).isEmpty()) throw new AlreadyLinkedException();
        }
    }

    public boolean unlink(long chatId, String provider) {
        return jdbc.update("DELETE FROM user_identities WHERE chat_id = ? AND provider = ?", chatId, provider) > 0;
    }

    public List<Map<String, Object>> identities(long chatId) {
        return jdbc.queryForList("""
            SELECT provider, email, name, created_at AS "connectedAt"
            FROM user_identities WHERE chat_id = ? ORDER BY created_at
            """, chatId);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
