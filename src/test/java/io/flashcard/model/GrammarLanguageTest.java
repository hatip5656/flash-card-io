package io.flashcard.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrammarLanguageTest {

    @Test
    void turkishLearnersGetTurkish() {
        assertEquals("tr", GrammarLanguage.forLearner("turkish"));
    }

    @Test
    void everyoneElseGetsEnglish() {
        assertEquals("en", GrammarLanguage.forLearner("english"));
        assertEquals("en", GrammarLanguage.forLearner(null));
    }

    @Test
    void adminLanguageDefaultsToTurkish() {
        assertEquals("tr", GrammarLanguage.parse(null));
        assertEquals("en", GrammarLanguage.parse("en"));
        assertEquals("tr", GrammarLanguage.parse("tr"));
    }

    @Test
    void unknownAdminLanguageIsRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> GrammarLanguage.parse("de"));
        assertEquals("lang must be tr or en", e.getMessage());
    }
}
