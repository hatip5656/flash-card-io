package io.flashcard.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrammarBankServiceTest {

    @Test
    void podcastInfoIsForTheRequestedLanguage() {
        var jdbc = mock(JdbcTemplate.class);
        var row = new HashMap<String, Object>();
        row.put("lesson_id", "a1-x");
        row.put("podcast_id", "pod-en");
        row.put("audio_cache_key", "pod-en.wav");
        row.put("duration_seconds", 80);
        row.put("has_video", true);
        when(jdbc.queryForList(contains("gp.language = ?"), eq("en"))).thenReturn(List.of(row));

        var info = new GrammarBankService(jdbc).getPodcastInfo("en");

        assertEquals("https://wordagram.hatip.dev/podcasts/pod-en.wav", info.get("a1-x").get("podcastUrl"));
        assertEquals(true, info.get("a1-x").get("hasVideo"));
        verify(jdbc, never()).queryForList(contains("g.podcast_id"));
    }

    @Test
    void noRowsMeansNoPodcasts() {
        var jdbc = mock(JdbcTemplate.class);
        assertTrue(new GrammarBankService(jdbc).getPodcastInfo("en").isEmpty());
    }
}
