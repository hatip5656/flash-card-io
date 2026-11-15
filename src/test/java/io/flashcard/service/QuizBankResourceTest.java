package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.service.QuizBankService.BankFile;
import io.flashcard.service.QuizBankService.Entry;
import io.flashcard.service.QuizBankService.Item;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The shipped quiz bank: every item must be a well-formed question with exactly one right answer. */
class QuizBankResourceTest {

    private static final Set<String> POS = Set.of("noun", "verb", "adjective", "adverb", "pronoun", "numeral",
        "conjunction", "adposition", "interjection", "phrase", "other");

    private static List<Entry> entries() throws Exception {
        try (InputStream in = new ClassPathResource(QuizBankService.RESOURCE).getInputStream()) {
            return new ObjectMapper().readValue(in, BankFile.class).words();
        }
    }

    @Test
    void everyItemIsAWellFormedQuestion() throws Exception {
        List<Entry> entries = entries();
        assertTrue(entries.size() > 1500, "bank covers the word list: " + entries.size());
        for (Entry e : entries) {
            assertTrue(POS.contains(e.pos()), e.estonian() + " pos " + e.pos());
            for (Item i : e.items()) {
                String where = e.estonian() + ": " + i.sentence();
                assertEquals(1, i.gap().split("___", -1).length - 1, where);
                assertEquals(i.sentence(), i.gap().replace("___", i.answer()), where);
                for (List<String> options : List.of(i.formOptions(), i.wordOptions())) {
                    assertTrue(options.isEmpty() || options.size() == 3, where);
                    Set<String> all = new HashSet<>();
                    all.add(i.answer().toLowerCase(Locale.ROOT));
                    for (String o : options) assertTrue(all.add(o.toLowerCase(Locale.ROOT)), where + " repeats " + o);
                }
            }
        }
    }

    @Test
    void serviceLoadsTheShippedBank() {
        QuizBankService service = new QuizBankService(new ObjectMapper());
        assertTrue(service.get("elama").isPresent());
        assertEquals("verb", service.get("elama").get().pos());
    }
}
