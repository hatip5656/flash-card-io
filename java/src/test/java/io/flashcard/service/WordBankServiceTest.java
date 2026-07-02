package io.flashcard.service;

import io.flashcard.model.Word;
import io.flashcard.repository.WordDbRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WordBankServiceTest {

    private WordDbRepository wordDbRepo;
    private WordBankService service;

    @BeforeEach
    void setUp() {
        wordDbRepo = mock(WordDbRepository.class);
        service = new WordBankService(wordDbRepo);
    }

    private Word makeWord(String id, String estonian, String english, String level) {
        Word w = new Word();
        w.setId(id);
        w.setEstonian(estonian);
        w.setEnglish(english);
        w.setCefrLevel(level);
        w.setSentences(List.of());
        return w;
    }

    @Test
    void reloadLoadsWordsFromDb() {
        List<Word> words = List.of(
            makeWord("a1-tere", "tere", "hello", "A1"),
            makeWord("a1-jah", "jah", "yes", "A1"),
            makeWord("b1-raamat", "raamat", "book", "B1")
        );
        when(wordDbRepo.loadAllWords()).thenReturn(words);

        service.reload();

        assertEquals(3, service.getAllWords().size());
    }

    @Test
    void getWordsForLevelFiltersCorrectly() {
        List<Word> words = List.of(
            makeWord("a1-tere", "tere", "hello", "A1"),
            makeWord("a1-jah", "jah", "yes", "A1"),
            makeWord("b1-raamat", "raamat", "book", "B1")
        );
        when(wordDbRepo.loadAllWords()).thenReturn(words);
        service.reload();

        assertEquals(2, service.getWordsForLevel("A1").size());
        assertEquals(1, service.getWordsForLevel("B1").size());
        assertEquals(0, service.getWordsForLevel("B2").size());
    }

    @Test
    void getWordByIdReturnsCorrectWord() {
        Word word = makeWord("a1-tere", "tere", "hello", "A1");
        when(wordDbRepo.loadAllWords()).thenReturn(List.of(word));
        service.reload();

        assertEquals(word, service.getWordById("a1-tere"));
        assertNull(service.getWordById("nonexistent"));
    }

    @Test
    void getUnsentExcludesSentIds() {
        List<Word> words = List.of(
            makeWord("a1-tere", "tere", "hello", "A1"),
            makeWord("a1-jah", "jah", "yes", "A1"),
            makeWord("a1-ei", "ei", "no", "A1")
        );
        when(wordDbRepo.loadAllWords()).thenReturn(words);
        service.reload();

        List<Word> unsent = service.getUnsent("A1", Set.of("a1-tere", "a1-jah"));
        assertEquals(1, unsent.size());
        assertEquals("ei", unsent.get(0).getEstonian());
    }

    @Test
    void getUnsentReturnsEmptyForDifferentLevel() {
        List<Word> words = List.of(makeWord("a1-tere", "tere", "hello", "A1"));
        when(wordDbRepo.loadAllWords()).thenReturn(words);
        service.reload();

        assertTrue(service.getUnsent("B1", Set.of()).isEmpty());
    }
}
