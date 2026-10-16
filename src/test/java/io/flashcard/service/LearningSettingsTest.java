package io.flashcard.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LearningSettingsTest {

    private final LearningSettings settings = LearningSettings.of(Map.of(
        "readiness.mock_threshold", 0.7, "plan.exam_items", 2,
        "plan.level_order", List.of("A1", "A2", "B1")));

    @Test
    void readsNumbersCountsAndLists() {
        assertEquals(0.7, settings.num("readiness.mock_threshold"), 1e-9);
        assertEquals(2, settings.count("plan.exam_items"));
        assertEquals(List.of("A1", "A2", "B1"), settings.list("plan.level_order"));
    }

    @Test
    void aMissingOrMistypedSettingIsAnErrorNotADefault() {
        assertThrows(IllegalStateException.class, () -> settings.num("no.such.key"));
        assertThrows(IllegalStateException.class, () -> settings.num("plan.level_order"));
        assertThrows(IllegalStateException.class, () -> settings.list("plan.exam_items"));
    }
}
