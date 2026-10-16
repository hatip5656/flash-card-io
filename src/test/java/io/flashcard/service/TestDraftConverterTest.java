package io.flashcard.service;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestDraftConverterTest {

    private static final TestDraftConverter.Config CONFIG = new TestDraftConverter.Config(
        List.of("et_f_reporter", "et_f_rohemae"), List.of("et_m_kelmsaar"), 700, 30);

    private static Map<String, Object> draft(Map<String, Object>... tasks) {
        return Map.of("title", "Test", "tasks", List.of(tasks));
    }

    @Test
    void dialogueGetsOneAudioFileWithAVoicePerSpeaker() {
        var task = Map.<String, Object>of(
            "type", "listening_dialogue", "instruction", "Kuula dialoogi.",
            "script", List.of(
                Map.of("speaker", "Mari", "gender", "f", "text", "Tere! Kas sul on aega?"),
                Map.of("speaker", "Jaan", "gender", "m", "text", "Jah, mis juhtus?"),
                Map.of("speaker", "Mari", "gender", "f", "text", "Lähme kinno.")),
            "questions", List.of(
                Map.of("type", "multiple_choice", "prompt", "Kuhu Mari tahab minna?",
                    "options", List.of("kinno", "poodi", "tööle"), "correctIndex", 0),
                Map.of("type", "true_false", "prompt", "Jaanil ei ole aega.", "answer", false)));

        var r = TestDraftConverter.convert(draft(task), "gen-b1-listening-abc", CONFIG);

        assertTrue(r.ok(), () -> r.errors().toString());
        assertEquals(1, r.audio().size());
        var job = r.audio().get(0);
        assertTrue(job.key().matches("generated/gen-b1-listening-abc/t1-[0-9a-f]{10}\\.mp3"), job.key());
        assertEquals(List.of("et_f_reporter", "et_m_kelmsaar", "et_f_reporter"),
            job.segments().stream().map(TestDraftConverter.Segment::voice).toList());
        assertEquals(2, r.questions().size());
        assertEquals(List.of(job.key()), r.questions().get(0).get("audio"));
        // true/false becomes a two-option choice
        assertEquals(List.of("Õige", "Vale"), r.questions().get(1).get("options"));
        assertEquals(1, r.questions().get(1).get("correctIndex"));
        assertTrue(((String) r.questions().get(0).get("transcript")).startsWith("Mari: Tere!"));
    }

    @Test
    void heardSentenceItemsGetTheirOwnAudioAndPointAtTheSentence() {
        var task = Map.<String, Object>of("type", "heard_sentence", "instruction", "Vali lause, mida kuulsid.",
            "questions", List.of(Map.of("type", "heard_sentence", "sentence", "Ma elan Tartus.",
                "options", List.of("Ma elan Tartusse.", "Ma elan Tartus.", "Ma elasin Tartus."))));

        var r = TestDraftConverter.convert(draft(task), "gen-a2-dictation-x", CONFIG);

        assertTrue(r.ok(), () -> r.errors().toString());
        assertEquals(1, r.questions().get(0).get("correctIndex"));
        assertTrue(r.audio().get(0).key().startsWith("generated/gen-a2-dictation-x/t1q1-"), r.audio().get(0).key());
        assertEquals("Ma elan Tartus.", r.audio().get(0).segments().get(0).text());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void heardSentenceOptionsThatAreNotNearIdenticalAreFlagged() {
        var task = Map.<String, Object>of("type", "heard_sentence",
            "questions", List.of(Map.of("type", "heard_sentence", "sentence", "Ma elan Tartus.",
                "options", List.of("Ma elan Tartus.", "Täna sajab vihma väga tugevasti."))));

        var r = TestDraftConverter.convert(draft(task), "gen-x", CONFIG);

        assertTrue(r.ok());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("not a near-identical pair")), () -> r.warnings().toString());
    }

    @Test
    void structuralProblemsAreErrors() {
        var badChoice = Map.<String, Object>of("type", "grammar_items", "questions", List.of(
            Map.of("type", "multiple_choice", "prompt", "Ma ... kodus.", "options", List.of("olen", "olen"), "correctIndex", 0),
            Map.of("type", "multiple_choice", "prompt", "Ta ... tööl.", "options", List.of("on", "oli"), "correctIndex", 5)));
        var noScript = Map.<String, Object>of("type", "listening_monologue", "questions", List.of());
        var noPassage = Map.<String, Object>of("type", "reading_text", "questions", List.of());
        var wrongSentence = Map.<String, Object>of("type", "heard_sentence", "questions", List.of(
            Map.of("type", "heard_sentence", "sentence", "Ma elan Tartus.", "options", List.of("A.", "B."))));

        var r = TestDraftConverter.convert(draft(badChoice, noScript, noPassage, wrongSentence), "gen-x", CONFIG);

        assertFalse(r.ok());
        var errors = String.join(" | ", r.errors());
        assertTrue(errors.contains("duplicate option"), errors);
        assertTrue(errors.contains("correctIndex out of range"), errors);
        assertTrue(errors.contains("needs a script"), errors);
        assertTrue(errors.contains("needs a passage"), errors);
        assertTrue(errors.contains("contain the sentence exactly once"), errors);
    }

    @Test
    void readingPassageGoesIntoTheContext() {
        var task = Map.<String, Object>of("type", "reading_text", "instruction", "Loe kirja.", "passage", "Tere, Kati!",
            "questions", List.of(Map.of("type", "multiple_choice", "prompt", "Kellele kiri on?",
                "options", List.of("Katile", "Marile"), "correctIndex", 0)));

        var r = TestDraftConverter.convert(draft(task), "gen-x", CONFIG);

        assertTrue(r.ok());
        assertEquals("Loe kirja.\n\nTere, Kati!", r.questions().get(0).get("context"));
        assertTrue(r.audio().isEmpty());
        assertEquals("1. ülesanne", r.questions().get(0).get("section"));
    }

    @Test
    void changedAudioGetsANewKey() {
        var task = new HashMap<String, Object>(Map.of("type", "listening_monologue", "instruction", "Kuula.",
            "script", List.of(Map.of("speaker", "A", "gender", "f", "text", "Tere!")),
            "questions", List.of(Map.of("type", "true_false", "prompt", "x", "answer", true))));
        String before = TestDraftConverter.convert(draft(task), "gen-x", CONFIG).audio().get(0).key();
        String same = TestDraftConverter.convert(draft(task), "gen-x", CONFIG).audio().get(0).key();
        task.put("script", List.of(Map.of("speaker", "A", "gender", "f", "text", "Tere õhtust!")));
        String changed = TestDraftConverter.convert(draft(task), "gen-x", CONFIG).audio().get(0).key();
        String slower = TestDraftConverter.convert(draft(task), "gen-x",
            new TestDraftConverter.Config(CONFIG.femaleVoices(), CONFIG.maleVoices(), 700, 30, 0.9)).audio().get(0).key();
        assertEquals(before, same);
        assertNotEquals(before, changed);
        assertNotEquals(changed, slower);
    }

    @Test
    void wordDifferenceCountsChangedWords() {
        assertEquals(1, TestDraftConverter.wordDifference("Ma elan Tartus.", "Ma elan Tartusse."));
        assertEquals(0, TestDraftConverter.wordDifference("Ma elan Tartus.", "ma elan tartus"));
        assertEquals(2, TestDraftConverter.wordDifference("Ma elan Tartus.", "Ma elasin Tartus täna."));
    }

    private static final TestDraftConverter.Config THREE = new TestDraftConverter.Config(
        List.of("et_f_reporter", "et_f_rohemae", "et_f_sobak"), List.of("et_m_kelmsaar", "et_m_kilumets"), 700, 30);

    private static List<String> voices(TestDraftConverter.Result r) {
        return r.audio().get(0).segments().stream().map(TestDraftConverter.Segment::voice).toList();
    }

    @Test
    void threeSpeakersGetThreeDifferentVoices() {
        var task = Map.<String, Object>of("type", "listening_dialogue", "instruction", "Kuula.",
            "script", List.of(
                Map.of("speaker", "Mari", "gender", "f", "text", "Tere!"),
                Map.of("speaker", "Liis", "gender", "f", "text", "Tere, Mari!"),
                Map.of("speaker", "Toomas", "gender", "m", "text", "Tere mõlemale!"),
                Map.of("speaker", "Mari", "gender", "f", "text", "Kuidas läheb?")),
            "questions", List.of(Map.of("type", "true_false", "prompt", "Mari tervitab.", "answer", true)));

        var r = TestDraftConverter.convert(draft(task), "gen-x", THREE);

        assertTrue(r.ok(), () -> r.errors().toString());
        assertEquals(List.of("et_f_reporter", "et_f_rohemae", "et_m_kelmsaar", "et_f_reporter"), voices(r));
        assertTrue(r.warnings().isEmpty(), () -> r.warnings().toString());
    }

    @Test
    void pinnedVoicesAreKeptAndOthersAvoidThem() {
        var task = Map.<String, Object>of("type", "listening_dialogue", "instruction", "Kuula.",
            "speakers", List.of(Map.of("name", "Mari", "gender", "f", "voice", "et_f_reporter"),
                                Map.of("name", "Jaan", "gender", "m", "voice", "et_m_kilumets")),
            "script", List.of(
                Map.of("speaker", "Liis", "gender", "f", "text", "Tere!"),
                Map.of("speaker", "Mari", "text", "Tere, Liis!"),
                Map.of("speaker", "Jaan", "text", "Tere!")),
            "questions", List.of(Map.of("type", "true_false", "prompt", "Liis tervitab.", "answer", true)));

        var r = TestDraftConverter.convert(draft(task), "gen-x", THREE);

        // Liis is not pinned: she gets the first female voice Mari does not use
        assertEquals(List.of("et_f_rohemae", "et_f_reporter", "et_m_kilumets"), voices(r));
    }

    @Test
    void unknownAndSharedVoicesAreFlagged() {
        var task = Map.<String, Object>of("type", "listening_dialogue", "instruction", "Kuula.",
            "script", List.of(
                Map.of("speaker", "Mari", "gender", "f", "voice", "et_f_sobak", "text", "Tere!"),
                Map.of("speaker", "Liis", "gender", "f", "voice", "et_f_sobak", "text", "Tere!"),
                Map.of("speaker", "Ott", "gender", "m", "voice", "robot", "text", "Tere!")),
            "questions", List.of(Map.of("type", "true_false", "prompt", "x", "answer", true)));

        var r = TestDraftConverter.convert(draft(task), "gen-x", THREE);

        var warnings = String.join(" | ", r.warnings());
        assertTrue(warnings.contains("unknown voice 'robot'"), warnings);
        assertTrue(warnings.contains("Mari and Liis share the voice et_f_sobak"), warnings);
        assertEquals("et_m_kelmsaar", voices(r).get(2));
    }
}
