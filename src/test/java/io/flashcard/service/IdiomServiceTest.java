package io.flashcard.service;

import io.flashcard.model.Idiom;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IdiomServiceTest {

    private final IdiomService service = new IdiomService();

    @Test
    void getAllIdiomsReturns15() {
        List<Idiom> idioms = service.getAllIdioms();
        assertEquals(15, idioms.size());
    }

    @Test
    void allIdiomsHaveFields() {
        for (Idiom idiom : service.getAllIdioms()) {
            assertNotNull(idiom.estonian());
            assertNotNull(idiom.english());
            assertNotNull(idiom.meaning());
            assertFalse(idiom.estonian().isBlank());
            assertFalse(idiom.english().isBlank());
        }
    }

    @Test
    void getRandomIdiomReturnsValidIdiom() {
        Idiom idiom = service.getRandomIdiom();
        assertNotNull(idiom);
        assertTrue(service.getAllIdioms().contains(idiom));
    }

    @Test
    void getRandomIdiomHasVariation() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(service.getRandomIdiom().estonian());
        }
        assertTrue(seen.size() > 1, "Random should return more than one unique idiom over 50 calls");
    }
}
