package io.flashcard.service;

import io.flashcard.model.Word;
import io.flashcard.service.QuizBankService.Entry;
import io.flashcard.service.QuizBankService.Item;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class QuizGeneratorTest {

    private static Word word(String est, String en, String tr, String level, Word.Sentence... sentences) {
        Word w = new Word();
        w.setId(level.toLowerCase() + "-" + est);
        w.setEstonian(est);
        w.setEnglish(en);
        w.setTurkish(tr);
        w.setCefrLevel(level);
        w.setSentences(List.of(sentences));
        return w;
    }

    private static final List<Word> BANK = List.of(
        word("kirjutama", "write", "yazmak", "A1",
            new Word.Sentence("Ma kirjutan kirja.", "I am writing a letter.", "Bir mektup yazıyorum.")),
        word("lugema", "read", "okumak", "A1"),
        word("sööma", "eat", "yemek", "A1"),
        word("jooma", "drink", "içmek", "A1"),
        word("magama", "sleep", "uyumak", "A1"),
        word("maja", "house", "ev", "A1"),
        word("koer", "dog", "köpek", "A1"),
        word("laud", "table", "masa", "A1"),
        word("auto", "car", "araba", "A1"),
        word("moto", "slogan", "slogan", "B2"),
        word("loosung", "slogan", "slogan", "B2"),
        word("hüüdlause", "slogan", "slogan", "B2"),
        word("tunnuslause", "motto", "slogan / parola", "B2"),
        word("riisuma", "rake", "rake", "B2"),       // Turkish never filled in: English stored as Turkish
        word("tühi", "empty", null, "A1"));

    private static final Map<String, Entry> ITEMS = Map.of(
        "kirjutama", new Entry("kirjutama", "verb", List.of(
            new Item("Ma kirjutan kirja.", "Ma ___ kirja.", "kirjutan", "IndPrSg1",
                List.of("kirjutab", "kirjutama", "kirjutada"),
                List.of("magan", "joon", "söön")))),
        "lugema", new Entry("lugema", "verb", List.of()),
        "sööma", new Entry("sööma", "verb", List.of()),
        "jooma", new Entry("jooma", "verb", List.of()),
        "magama", new Entry("magama", "verb", List.of()),
        "riisuma", new Entry("riisuma", "verb", List.of(), Set.of("tr")), // audit: Turkish is untranslated
        "maja", new Entry("maja", "noun", List.of()),
        "koer", new Entry("koer", "noun", List.of()),
        "laud", new Entry("laud", "noun", List.of()),
        "auto", new Entry("auto", "noun", List.of()));

    private static QuizGenerator generator() {
        Map<String, Entry> items = new HashMap<>(ITEMS);
        for (String w : List.of("moto", "loosung", "hüüdlause", "tunnuslause")) items.put(w, new Entry(w, "noun", List.of()));
        items.put("tühi", new Entry("tühi", "adjective", List.of()));
        return new QuizGenerator(new QuizBankService(items), BANK);
    }

    private static final List<String> LEARNED = BANK.stream().map(Word::getEstonian).toList();

    private static String turkishOf(String est) {
        return BANK.stream().filter(w -> w.getEstonian().equals(est)).findFirst().orElseThrow().getTurkish();
    }

    @Test
    void meaningOptionsAreAllTheSameWordType() {
        for (int seed = 0; seed < 30; seed++) {
            var q = generator().question("kirjutama", QuizGenerator.Kind.TO_NATIVE, LEARNED, true, new Random(seed)).orElseThrow();
            @SuppressWarnings("unchecked") List<String> options = (List<String>) q.get("options");
            assertEquals(4, options.size());
            assertEquals("yazmak", options.get((int) q.get("correctIndex")));
            // Only verbs: a noun among them would be picked out by its form alone
            Set<String> verbs = Set.of("yazmak", "okumak", "yemek", "içmek", "uyumak");
            assertTrue(verbs.containsAll(options), "non-verb option in " + options);
        }
    }

    @Test
    void estonianOptionsAreAllTheSameWordType() {
        for (int seed = 0; seed < 30; seed++) {
            var q = generator().question("maja", QuizGenerator.Kind.TO_ESTONIAN, LEARNED, true, new Random(seed)).orElseThrow();
            @SuppressWarnings("unchecked") List<String> options = (List<String>) q.get("options");
            assertEquals("ev", q.get("prompt"));
            Set<String> nouns = Set.of("maja", "koer", "laud", "auto", "moto", "loosung", "hüüdlause", "tunnuslause");
            assertTrue(nouns.containsAll(options), "non-noun option in " + options);
        }
    }

    @Test
    void turkishLearnerNeverGetsEnglish() {
        // No Turkish, or English stored as the Turkish: never asked, never offered as an option
        assertTrue(generator().question("tühi", QuizGenerator.Kind.TO_NATIVE, LEARNED, true, new Random(1)).isEmpty());
        assertTrue(generator().question("riisuma", QuizGenerator.Kind.TO_NATIVE, LEARNED, true, new Random(1)).isEmpty());
        assertTrue(generator().question("riisuma", QuizGenerator.Kind.TO_ESTONIAN, LEARNED, true, new Random(1)).isEmpty());
        for (int seed = 0; seed < 30; seed++) {
            var q = generator().question("lugema", QuizGenerator.Kind.TO_NATIVE, LEARNED, true, new Random(seed)).orElseThrow();
            assertFalse(((List<?>) q.get("options")).contains("rake"));
        }
    }

    @Test
    void englishLearnerGetsEnglish() {
        var q = generator().question("kirjutama", QuizGenerator.Kind.TO_NATIVE, LEARNED, false, new Random(3)).orElseThrow();
        assertEquals("write", ((List<?>) q.get("options")).get((int) q.get("correctIndex")));
        assertEquals("est-to-eng", q.get("type"));
    }

    @Test
    void synonymsAreNeverOfferedAsWrongOptions() {
        for (int seed = 0; seed < 30; seed++) {
            var q = generator().question("moto", QuizGenerator.Kind.TO_NATIVE, LEARNED, true, new Random(seed));
            // moto, loosung, hüüdlause all mean "slogan" and tunnuslause partly does: no fair wrong option is left
            q.ifPresent(question -> {
                @SuppressWarnings("unchecked") List<String> options = (List<String>) question.get("options");
                assertEquals(1, options.stream().filter(o -> o.contains("slogan")).count(), options.toString());
            });
        }
    }

    @Test
    void formChoiceAsksForTheRightFormOfTheWordInASentence() {
        var q = generator().question("kirjutama", QuizGenerator.Kind.FORM_CHOICE, LEARNED, true, new Random(1)).orElseThrow();
        assertEquals("form_choice", q.get("type"));
        assertEquals("Ma ___ kirja.", q.get("prompt"));
        @SuppressWarnings("unchecked") List<String> options = (List<String>) q.get("options");
        assertEquals(Set.of("kirjutan", "kirjutab", "kirjutama", "kirjutada"), new HashSet<>(options));
        assertEquals("kirjutan", options.get((int) q.get("correctIndex")));
        assertEquals("yazmak", q.get("clue"));
        assertEquals("Bir mektup yazıyorum.", q.get("translation"));
    }

    @Test
    void wordChoiceGivesNoClueThatNamesTheAnswer() {
        var q = generator().question("kirjutama", QuizGenerator.Kind.WORD_CHOICE, LEARNED, true, new Random(1)).orElseThrow();
        assertEquals("word_choice", q.get("type"));
        @SuppressWarnings("unchecked") List<String> options = (List<String>) q.get("options");
        assertEquals(Set.of("kirjutan", "magan", "joon", "söön"), new HashSet<>(options));
        assertNull(q.get("clue"));
        assertEquals("Bir mektup yazıyorum.", q.get("translation"));
    }

    @Test
    void typedGapGivesTheDictionaryFormAndMeaningAsTheClue() {
        var q = generator().question("kirjutama", QuizGenerator.Kind.TYPED, LEARNED, true, new Random(1)).orElseThrow();
        assertEquals("fill_blank", q.get("type"));
        assertEquals("Ma ___ kirja.", q.get("prompt"));
        assertEquals("kirjutan", q.get("correctAnswer"));
        assertEquals("kirjutama", q.get("lemma"));
        assertEquals("yazmak", q.get("clue"));
    }

    @Test
    void neverAsksToTypeTheFormTheClueAlreadyShows() {
        Map<String, Entry> items = new HashMap<>(ITEMS);
        items.put("maja", new Entry("maja", "noun", List.of(
            new Item("See on maja.", "See on ___.", "maja", "SgN", List.of("majas", "majja", "majast"), List.of()))));
        var gen = new QuizGenerator(new QuizBankService(items), BANK);
        assertTrue(gen.question("maja", QuizGenerator.Kind.TYPED, LEARNED, true, new Random(1)).isEmpty());
        assertTrue(gen.question("maja", QuizGenerator.Kind.FORM_CHOICE, LEARNED, true, new Random(1)).isPresent());
    }

    @Test
    void wordsWithoutSentenceItemsCannotBeGapQuestions() {
        assertTrue(generator().question("maja", QuizGenerator.Kind.FORM_CHOICE, LEARNED, true, new Random(1)).isEmpty());
    }

    @Test
    void quizHasOneQuestionPerWordAndSkipsTypedWhenNotWanted() {
        for (int seed = 0; seed < 20; seed++) {
            List<Map<String, Object>> qs = generator().questions(LEARNED, LEARNED, 10, true, false, new Random(seed));
            assertEquals(10, qs.size());
            assertTrue(qs.stream().noneMatch(q -> "fill_blank".equals(q.get("type"))));
            Set<Object> words = new HashSet<>();
            for (var q : qs) assertTrue(words.add(((Map<?, ?>) q.get("word")).get("estonian")), "repeated word");
            for (int i = 0; i < qs.size(); i++) assertEquals(i, qs.get(i).get("index"));
        }
    }

    @Test
    void wordsMissingFromTheBankAreSkipped() {
        List<String> learned = new ArrayList<>(LEARNED);
        learned.add(0, "olümpiamängud");
        List<Map<String, Object>> qs = generator().questions(learned, learned, 50, true, true, new Random(4));
        assertTrue(qs.stream().noneMatch(q -> "olümpiamängud".equals(((Map<?, ?>) q.get("word")).get("estonian"))));
    }

    @Test
    void gapQuestionsAppearRegularly() {
        int gaps = 0;
        for (int seed = 0; seed < 200; seed++) {
            var qs = generator().questions(List.of("kirjutama"), LEARNED, 1, true, true, new Random(seed));
            String type = (String) qs.get(0).get("type");
            if (type.equals("form_choice") || type.equals("word_choice") || type.equals("fill_blank")) gaps++;
        }
        assertTrue(gaps > 100, "exam-style questions should be the majority for words that have them: " + gaps);
    }

    @Test
    void verbQuestionPromptStaysInTheLearnersLanguage() {
        assertEquals("yazmak", turkishOf("kirjutama"));
        var q = generator().question("kirjutama", QuizGenerator.Kind.TO_ESTONIAN, LEARNED, true, new Random(2)).orElseThrow();
        assertEquals("yazmak", q.get("prompt"));
        assertEquals("tr-to-est", q.get("type"));
    }
}
