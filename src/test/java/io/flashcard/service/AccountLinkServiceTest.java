package io.flashcard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountLinkServiceTest {

    private JdbcTemplate jdbc;
    private AccountLinkService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new AccountLinkService(jdbc);
    }

    // ── generateLinkCode ──

    @Test
    void generateLinkCodeReturns6Digits() {
        String code = service.generateLinkCode(2000000001L);
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void generateLinkCodeDeletesPreviousCodes() {
        service.generateLinkCode(2000000001L);
        verify(jdbc).update(contains("DELETE FROM link_codes"), eq(2000000001L));
    }

    @Test
    void generateLinkCodeInsertsNewCode() {
        service.generateLinkCode(2000000001L);
        verify(jdbc).update(contains("INSERT INTO link_codes"), anyString(), eq(2000000001L), any(Timestamp.class));
    }

    // ── connectTelegram ──

    @Test
    void connectTelegramWithValidCodeReturnsLinkedMobileId() {
        long mobileId = 2000000001L;
        long telegramId = 123456789L;

        // Valid unexpired code
        when(jdbc.queryForList(contains("FROM link_codes"), eq("482916")))
            .thenReturn(List.of(Map.of("chat_id", mobileId)));
        // Not already linked
        when(jdbc.queryForObject(contains("FROM linked_accounts"), eq(Integer.class), eq(telegramId)))
            .thenReturn(0);

        Optional<Long> result = service.connectTelegram(telegramId, "482916");

        assertTrue(result.isPresent());
        assertEquals(mobileId, result.get());
        // Verify code marked as used
        verify(jdbc).update(contains("SET used = TRUE"), eq("482916"));
        // Verify link created
        verify(jdbc).update(contains("INSERT INTO linked_accounts"), eq(mobileId), eq(telegramId));
    }

    @Test
    void connectTelegramWithExpiredCodeReturnsEmpty() {
        when(jdbc.queryForList(contains("FROM link_codes"), eq("000000")))
            .thenReturn(List.of()); // No valid code found

        Optional<Long> result = service.connectTelegram(123456789L, "000000");

        assertTrue(result.isEmpty());
        verify(jdbc, never()).update(contains("INSERT INTO linked_accounts"), anyLong(), anyLong());
    }

    @Test
    void connectTelegramAlreadyLinkedReturnsEmpty() {
        long telegramId = 123456789L;

        when(jdbc.queryForList(contains("FROM link_codes"), eq("482916")))
            .thenReturn(List.of(Map.of("chat_id", 2000000001L)));
        // Already linked
        when(jdbc.queryForObject(contains("FROM linked_accounts"), eq(Integer.class), eq(telegramId)))
            .thenReturn(1);

        Optional<Long> result = service.connectTelegram(telegramId, "482916");

        assertTrue(result.isEmpty());
    }

    // ── getEffectiveChatId ──

    @Test
    void getEffectiveChatIdReturnsLinkedMobileId() {
        long telegramId = 123456789L;
        long mobileId = 2000000001L;

        when(jdbc.queryForList(contains("FROM linked_accounts WHERE telegram_id"), eq(Long.class), eq(telegramId)))
            .thenReturn(List.of(mobileId));

        assertEquals(mobileId, service.getEffectiveChatId(telegramId));
    }

    @Test
    void getEffectiveChatIdReturnsSelfWhenNotLinked() {
        long telegramId = 123456789L;

        when(jdbc.queryForList(contains("FROM linked_accounts WHERE telegram_id"), eq(Long.class), eq(telegramId)))
            .thenReturn(List.of());

        assertEquals(telegramId, service.getEffectiveChatId(telegramId));
    }

    // ── getLinkedTelegramId ──

    @Test
    void getLinkedTelegramIdReturnsIdWhenLinked() {
        long mobileId = 2000000001L;
        long telegramId = 123456789L;

        when(jdbc.queryForList(contains("FROM linked_accounts WHERE mobile_id"), eq(Long.class), eq(mobileId)))
            .thenReturn(List.of(telegramId));

        Optional<Long> result = service.getLinkedTelegramId(mobileId);
        assertTrue(result.isPresent());
        assertEquals(telegramId, result.get());
    }

    @Test
    void getLinkedTelegramIdReturnsEmptyWhenNotLinked() {
        when(jdbc.queryForList(contains("FROM linked_accounts WHERE mobile_id"), eq(Long.class), eq(2000000001L)))
            .thenReturn(List.of());

        assertTrue(service.getLinkedTelegramId(2000000001L).isEmpty());
    }

    // ── getLinkStatus ──

    @Test
    void getLinkStatusReturnsLinkedWithUsername() {
        long mobileId = 2000000001L;
        long telegramId = 123456789L;

        when(jdbc.queryForList(contains("FROM linked_accounts WHERE mobile_id"), eq(Long.class), eq(mobileId)))
            .thenReturn(List.of(telegramId));
        when(jdbc.queryForList(contains("SELECT username"), eq(String.class), eq(telegramId)))
            .thenReturn(List.of("hatip_test"));

        Map<String, Object> status = service.getLinkStatus(mobileId);

        assertEquals(true, status.get("linked"));
        assertEquals(telegramId, status.get("telegramId"));
        assertEquals("hatip_test", status.get("telegramUsername"));
    }

    @Test
    void getLinkStatusReturnsNotLinked() {
        when(jdbc.queryForList(contains("FROM linked_accounts WHERE mobile_id"), eq(Long.class), eq(2000000001L)))
            .thenReturn(List.of());

        Map<String, Object> status = service.getLinkStatus(2000000001L);

        assertEquals(false, status.get("linked"));
        assertFalse(status.containsKey("telegramId"));
    }

    // ── unlink ──

    @Test
    void unlinkDeletesLink() {
        service.unlink(2000000001L);
        verify(jdbc).update(contains("DELETE FROM linked_accounts"), eq(2000000001L));
    }

    // ── mergeAccounts ──

    @Test
    void mergeAccountsCopiesNewWordsAndCleansUp() {
        long mobileId = 2000000001L;
        long telegramId = 123456789L;

        // Return some rows affected for the merge operations
        when(jdbc.update(contains("INSERT INTO sent_words"), eq(mobileId), eq(telegramId), eq(mobileId)))
            .thenReturn(5);
        when(jdbc.update(contains("UPDATE sent_words m SET"), eq(mobileId), eq(telegramId)))
            .thenReturn(3);

        service.mergeAccounts(mobileId, telegramId);

        // Verify new words copied
        verify(jdbc).update(contains("INSERT INTO sent_words"), eq(mobileId), eq(telegramId), eq(mobileId));
        // Verify duplicate words merged
        verify(jdbc).update(contains("UPDATE sent_words m SET"), eq(mobileId), eq(telegramId));
        // Verify activity merged
        verify(jdbc).update(contains("INSERT INTO activity_log"), eq(mobileId), eq(telegramId));
        // Verify saved words copied
        verify(jdbc).update(contains("INSERT INTO saved_words"), eq(mobileId), eq(telegramId), eq(mobileId));
        // Verify grammar progress copied
        verify(jdbc).update(contains("INSERT INTO sent_grammar"), eq(mobileId), eq(telegramId), eq(mobileId));
        // Verify telegram data cleaned up
        verify(jdbc).update("DELETE FROM sent_words WHERE chat_id = ?", telegramId);
        verify(jdbc).update("DELETE FROM activity_log WHERE chat_id = ?", telegramId);
        verify(jdbc).update("DELETE FROM saved_words WHERE chat_id = ?", telegramId);
        verify(jdbc).update("DELETE FROM sent_grammar WHERE chat_id = ?", telegramId);
    }
}
