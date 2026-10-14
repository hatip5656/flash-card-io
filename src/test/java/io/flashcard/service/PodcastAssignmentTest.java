package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import io.flashcard.model.Podcast;
import io.flashcard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PodcastAssignmentTest {

    private static final long USER = 42L;

    @TempDir Path cacheDir;
    private PodcastRepository repo;
    private PodcastService service;

    @BeforeEach
    void setUp() {
        repo = mock(PodcastRepository.class);
        service = new PodcastService(mock(GeminiService.class), repo, mock(SentWordRepository.class),
            mock(SubscriberRepository.class), mock(QuizRepository.class), mock(ActivityRepository.class),
            mock(WordBankService.class), mock(AppProperties.class), mock(HttpClient.class),
            new ObjectMapper(), cacheDir.toString());
    }

    private static Map<String, Object> row(String id, String title, String audio, String lessonId) {
        var m = new HashMap<String, Object>();
        m.put("id", id);
        m.put("title", title);
        m.put("cefr_level", "A1");
        m.put("duration_seconds", 100);
        m.put("audio_cache_key", audio);
        m.put("lesson_id", lessonId);
        return m;
    }

    @Test
    void assignSkipsUsersWhoAlreadyHaveTheAudio() {
        var source = new Podcast();
        source.setAudioCacheKey("new.wav");
        when(repo.findById("src")).thenReturn(Optional.of(source));
        when(repo.userHasAudio(1L, "new.wav")).thenReturn(true);

        int assigned = service.assignToUsers("src", List.of(1L, 2L));

        assertEquals(1, assigned);
        verify(repo, never()).cloneForUser("src", 1L);
        verify(repo).cloneForUser("src", 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void splitsLibraryIntoAvailableAndAssigned() {
        when(repo.findAssignable()).thenReturn(List.of(
            row("g1", "Grammar: Negation", "neg-new.wav", "a1-negation"),
            row("g2", "Grammar: Numbers", "num.wav", "a1-numbers"),
            row("c1", "Aile kelimeleri", "aile.wav", null)));
        when(repo.findForUser(USER)).thenReturn(List.of(
            row("u1", "Grammar: Numbers", "num.wav", null),      // has the current version
            row("u2", "Grammar: Negation", "neg-old.wav", null), // has an older version
            row("u3", "Daily Estonian Lesson", "daily.wav", null)));
        when(repo.findTemplateAudioKeys()).thenReturn(List.of("neg-new.wav", "num.wav", "aile.wav", "neg-old.wav"));

        var result = service.assignmentsFor(USER);
        var available = (List<Map<String, Object>>) result.get("available");
        var assigned = (List<Map<String, Object>>) result.get("assigned");

        assertEquals(List.of("g1", "c1"), available.stream().map(m -> m.get("id")).toList());
        assertEquals("grammar", available.get(0).get("kind"));
        assertEquals("u2", available.get(0).get("olderCopyId"));
        assertEquals("custom", available.get(1).get("kind"));
        assertEquals(List.of("library", "outdated", "personal"), assigned.stream().map(m -> m.get("kind")).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void learnersOwnPodcastIsNeverAnOlderCopy() {
        // An admin podcast shares a generic title with the learner's own generated podcast
        when(repo.findAssignable()).thenReturn(List.of(row("c1", "Daily Estonian Lesson", "admin.wav", null)));
        when(repo.findForUser(USER)).thenReturn(List.of(row("u1", "Daily Estonian Lesson", "own.wav", null)));
        when(repo.findTemplateAudioKeys()).thenReturn(List.of("admin.wav"));

        var result = service.assignmentsFor(USER);
        var available = (List<Map<String, Object>>) result.get("available");
        var assigned = (List<Map<String, Object>>) result.get("assigned");

        assertEquals(null, available.get(0).get("olderCopyId"));
        assertEquals("personal", assigned.get(0).get("kind"));
    }
}
