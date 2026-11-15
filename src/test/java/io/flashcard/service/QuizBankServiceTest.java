package io.flashcard.service;

import io.flashcard.service.QuizBankService.Entry;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class QuizBankServiceTest {

    private static Map<String, Object> word(String est, String pos, String... bad) {
        Map<String, Object> r = new HashMap<>();
        r.put("estonian", est);
        r.put("pos", pos);
        r.put("quiz_bad_meaning", bad);
        return r;
    }

    private static Map<String, Object> item(String est, String sentence, String answer, String code,
                                            String[] forms, String[] words) {
        Map<String, Object> r = new HashMap<>();
        r.put("estonian", est);
        r.put("sentence", sentence);
        r.put("answer", answer);
        r.put("morph_code", code);
        r.put("form_options", forms);
        r.put("word_options", words);
        return r;
    }

    @Test
    void buildsTheGapFromTheSentenceInTheDatabase() {
        Map<String, Entry> bank = QuizBankService.fromRows(
            List.of(word("elama", "verb")),
            List.of(item("elama", "Ma elan Tallinnas.", "elan", "IndPrSg1",
                new String[]{"elab", "elame", "elama"}, new String[]{})));
        var it = bank.get("elama").items().get(0);
        assertEquals("Ma ___ Tallinnas.", it.gap());
        assertEquals(List.of("elab", "elame", "elama"), it.formOptions());
        assertEquals(List.of(), it.wordOptions());
        assertEquals("verb", bank.get("elama").pos());
    }

    @Test
    void skipsAnItemWhoseSentenceWasEditedAndNoLongerHoldsTheAnswer() {
        Map<String, Entry> bank = QuizBankService.fromRows(
            List.of(word("elama", "verb")),
            List.of(item("elama", "Me elame Tartus.", "elan", "IndPrSg1",
                new String[]{"elab", "elame", "elama"}, new String[]{})));
        assertTrue(bank.get("elama").items().isEmpty());
    }

    @Test
    void matchesTheAnswerAsAWholeWordOnly() {
        // "elan" inside "elanikud" must not be cut out
        Map<String, Entry> bank = QuizBankService.fromRows(
            List.of(word("elama", "verb")),
            List.of(item("elama", "Linna elanikud teavad, et ma elan siin.", "elan", "IndPrSg1",
                new String[]{"elab", "elame", "elama"}, new String[]{})));
        assertEquals("Linna elanikud teavad, et ma ___ siin.", bank.get("elama").items().get(0).gap());
    }

    @Test
    void keepsWordTypeAndBadMeaningFlagsForWordsWithoutItems() {
        Map<String, Entry> bank = QuizBankService.fromRows(List.of(word("riisuma", "verb", "tr")), List.of());
        assertEquals(Set.of("tr"), bank.get("riisuma").badMeaning());
        assertTrue(bank.get("riisuma").items().isEmpty());
    }
}
