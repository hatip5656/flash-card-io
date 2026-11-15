package io.flashcard.service;

import io.flashcard.model.Word;
import io.flashcard.service.QuizBankService.Entry;
import io.flashcard.service.QuizBankService.Item;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds quiz questions for the quiz screen, the feed's quiz cards and the home-screen widget.
 *
 * <p>Rules that keep a question honest:
 * <ul>
 *   <li>Every option is in one language: a Turkish learner never sees English. A word whose meaning in
 *       the learner's language is missing or known to be wrong is not asked as a meaning question.</li>
 *   <li>Wrong options are words of the same type (verbs with verbs), so the answer can't be picked
 *       by its ending; no wrong option shares a meaning with the answer.</li>
 *   <li>Exam-style gap questions (as in the screening test and the level checks) come from the
 *       hand-checked {@link QuizBankService} items: pick the right form of the word for the sentence,
 *       pick the word that fits the sentence, or type the form.</li>
 * </ul>
 */
public class QuizGenerator {

    public enum Kind { TO_NATIVE, TO_ESTONIAN, FORM_CHOICE, WORD_CHOICE, TYPED }

    private final QuizBankService bank;
    private final List<Word> words;
    private final Map<String, Word> byEstonian;

    public QuizGenerator(QuizBankService bank, List<Word> words) {
        this.bank = bank;
        this.words = words;
        this.byEstonian = words.stream().collect(Collectors.toMap(Word::getEstonian, Function.identity(), (a, b) -> a));
    }

    /**
     * Up to {@code count} questions, one per word, taking words from {@code candidates} in order and
     * skipping those that can't make a fair question. {@code learned} supplies wrong options first.
     */
    public List<Map<String, Object>> questions(List<String> candidates, List<String> learned, int count,
                                               boolean turkish, boolean typed, Random rnd) {
        return questions(candidates, learned, count, turkish, typed, null, rnd);
    }

    /** {@code level}: the learner's CEFR level; gap items testing forms above it wait. Null: no limit. */
    public List<Map<String, Object>> questions(List<String> candidates, List<String> learned, int count,
                                               boolean turkish, boolean typed, String level, Random rnd) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String est : candidates) {
            if (out.size() >= count) break;
            if (!used.add(est) || !byEstonian.containsKey(est)) continue;
            for (Kind kind : kindsToTry(est, typed, rnd)) {
                Optional<Map<String, Object>> q = question(est, kind, learned, turkish, level, rnd);
                if (q.isPresent()) {
                    q.get().put("index", out.size());
                    out.add(q.get());
                    break;
                }
            }
        }
        return out;
    }

    /** Kinds in a weighted random order: exam-style kinds first most of the time, when the word has them. */
    private List<Kind> kindsToTry(String est, boolean typed, Random rnd) {
        Map<Kind, Integer> weights = new EnumMap<>(Kind.class);
        boolean hasItems = bank.get(est).map(e -> !e.items().isEmpty()).orElse(false);
        if (hasItems) {
            // About three in four questions are exam-style for these words; meaning questions keep the rest
            weights.put(Kind.FORM_CHOICE, 45);
            weights.put(Kind.WORD_CHOICE, 15);
            if (typed) weights.put(Kind.TYPED, 15);
            weights.put(Kind.TO_NATIVE, 12);
            weights.put(Kind.TO_ESTONIAN, 13);
        } else {
            weights.put(Kind.TO_NATIVE, 50);
            weights.put(Kind.TO_ESTONIAN, 50);
        }
        List<Kind> order = new ArrayList<>();
        while (!weights.isEmpty()) {
            int total = weights.values().stream().mapToInt(Integer::intValue).sum();
            int pick = rnd.nextInt(total);
            for (var it = weights.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                pick -= e.getValue();
                if (pick < 0) { order.add(e.getKey()); it.remove(); break; }
            }
        }
        return order;
    }

    public Optional<Map<String, Object>> question(String est, Kind kind, List<String> learned, boolean turkish, Random rnd) {
        return question(est, kind, learned, turkish, null, rnd);
    }

    public Optional<Map<String, Object>> question(String est, Kind kind, List<String> learned, boolean turkish,
                                                  String level, Random rnd) {
        Word w = byEstonian.get(est);
        if (w == null) return Optional.empty();
        return switch (kind) {
            case TO_NATIVE, TO_ESTONIAN -> meaning(w, kind == Kind.TO_NATIVE, learned, turkish, rnd);
            case FORM_CHOICE, WORD_CHOICE, TYPED -> gap(w, kind, turkish, level, rnd);
        };
    }

    private static final List<String> LEVELS = List.of("A1", "A2", "B1", "B2", "C1");

    /** True when the item's form is at or below the learner's level (or either is unknown). */
    static boolean withinLevel(String itemLevel, String learnerLevel) {
        int item = itemLevel == null ? -1 : LEVELS.indexOf(itemLevel);
        int learner = learnerLevel == null ? -1 : LEVELS.indexOf(learnerLevel);
        return item < 0 || learner < 0 || item <= learner;
    }

    // ── Meaning questions ──

    private Optional<Map<String, Object>> meaning(Word w, boolean toNative, List<String> learned, boolean turkish, Random rnd) {
        String correctNative = nativeOf(w, turkish);
        if (correctNative == null) return Optional.empty();
        String pos = posOf(w.getEstonian());
        Set<String> taken = new HashSet<>(parts(correctNative));

        // Same-type words with a fair meaning: learned words first (shuffled), then the rest of the bank,
        // the target's own level first
        List<Word> learnedPool = new ArrayList<>(learned.stream().map(byEstonian::get).filter(Objects::nonNull).toList());
        Collections.shuffle(learnedPool, rnd);
        List<Word> bankPool = new ArrayList<>(words);
        Collections.shuffle(bankPool, rnd);
        bankPool.sort(Comparator.comparing((Word x) -> !Objects.equals(x.getCefrLevel(), w.getCefrLevel())));
        // Hand-picked confusables first (same field or look-alike: a real test of the meaning), shuffled
        List<Word> curated = new ArrayList<>(bank.get(w.getEstonian()).map(Entry::confusables).orElse(List.of())
            .stream().map(byEstonian::get).filter(Objects::nonNull).toList());
        Collections.shuffle(curated, rnd);
        List<Word> pool = new ArrayList<>(curated);
        pool.addAll(learnedPool);
        pool.addAll(bankPool);

        List<Word> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Word c : pool) {
            if (c.getEstonian().equals(w.getEstonian()) || !seen.add(c.getEstonian())) continue;
            if (!posOf(c.getEstonian()).equals(pos)) continue;
            String n = nativeOf(c, turkish);
            if (n == null) continue;
            Set<String> p = parts(n);
            if (!Collections.disjoint(p, taken)) continue; // a synonym would be a second right answer
            candidates.add(c);
            if (toNative && candidates.size() >= 3) break;
            if (!toNative && candidates.size() >= 30) break;
        }

        List<Word> distractors = new ArrayList<>();
        int curatedFit = (int) candidates.stream().filter(curated::contains).count();
        if (!toNative && curatedFit < 3) {
            // Look-alikes first: the learner has to know the word, not just its shape
            candidates.sort(Comparator.comparingDouble((Word c) -> -similarity(w.getEstonian(), c.getEstonian())));
            candidates = new ArrayList<>(candidates.subList(0, Math.min(6, candidates.size())));
            Collections.shuffle(candidates, rnd);
        }
        for (Word c : candidates) {
            Set<String> p = parts(nativeOf(c, turkish));
            if (!Collections.disjoint(p, taken)) continue;
            taken.addAll(p);
            distractors.add(c);
            if (distractors.size() == 3) break;
        }
        if (distractors.size() < 3) return Optional.empty();

        String correct = toNative ? correctNative : w.getEstonian();
        List<String> options = new ArrayList<>(distractors.stream()
            .map(c -> toNative ? nativeOf(c, turkish) : c.getEstonian()).toList());
        options.add(correct);
        Collections.shuffle(options, rnd);

        String lang = turkish ? "tr" : "eng";
        Map<String, Object> q = base(w, toNative ? "est-to-" + lang : lang + "-to-est",
            toNative ? w.getEstonian() : correctNative);
        q.put("options", options);
        q.put("correctIndex", options.indexOf(correct));
        return Optional.of(q);
    }

    // ── Exam-style gap questions ──

    private Optional<Map<String, Object>> gap(Word w, Kind kind, boolean turkish, String level, Random rnd) {
        Optional<Entry> entry = bank.get(w.getEstonian());
        if (entry.isEmpty()) return Optional.empty();
        List<Item> items = entry.get().items().stream()
            .filter(i -> withinLevel(i.level(), level))
            .filter(i -> switch (kind) {
                // The dictionary form is named in the clue and is the first guess: never the answer to pick
                case FORM_CHOICE -> i.formOptions() != null && i.formOptions().size() == 3
                    && !i.answer().equalsIgnoreCase(w.getEstonian());
                case WORD_CHOICE -> i.wordOptions() != null && i.wordOptions().size() == 3;
                // The clue shows the dictionary form: typing that same form back would test nothing
                default -> !i.answer().equalsIgnoreCase(w.getEstonian());
            }).toList();
        if (items.isEmpty()) return Optional.empty();
        Item item = items.get(rnd.nextInt(items.size()));
        String meaning = nativeOf(w, turkish);

        Map<String, Object> q;
        if (kind == Kind.TYPED) {
            q = base(w, "fill_blank", item.gap());
            q.put("correctAnswer", item.answer());
            q.put("options", List.of());
            q.put("correctIndex", -1);
            q.put("lemma", w.getEstonian());
            if (meaning != null) q.put("clue", meaning);
            // Older apps print the hint as "Hint: …": the dictionary form and meaning, not the first letter
            q.put("hint", meaning != null ? w.getEstonian() + " · " + meaning : w.getEstonian());
        } else {
            List<String> options = new ArrayList<>(kind == Kind.FORM_CHOICE ? item.formOptions() : item.wordOptions());
            options.add(item.answer());
            Collections.shuffle(options, rnd);
            q = base(w, kind == Kind.FORM_CHOICE ? "form_choice" : "word_choice", item.gap());
            q.put("options", options);
            q.put("correctIndex", options.indexOf(item.answer()));
            q.put("correctAnswer", item.answer());
            // Which word to use is the question in word_choice: its meaning would give it away
            if (kind == Kind.FORM_CHOICE) {
                q.put("lemma", w.getEstonian());
                if (meaning != null) q.put("clue", meaning);
            }
        }
        String translation = sentenceTranslation(w, item.sentence(), turkish);
        if (translation != null) q.put("translation", translation);
        return Optional.of(q);
    }

    // ── Helpers ──

    private static Map<String, Object> base(Word w, String type, String prompt) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("index", 0);
        q.put("type", type);
        q.put("prompt", prompt);
        q.put("word", Map.of("estonian", w.getEstonian(), "english", w.getEnglish() == null ? "" : w.getEnglish()));
        return q;
    }

    /** The word's meaning in the learner's language, or null when it is missing or known to be wrong. */
    String nativeOf(Word w, boolean turkish) {
        String value = turkish ? w.getTurkish() : w.getEnglish();
        if (value == null || value.isBlank()) return null;
        Set<String> bad = bank.get(w.getEstonian()).map(Entry::badMeaning).orElse(Set.of());
        if (bad.contains(turkish ? "tr" : "en")) return null;
        return value.trim();
    }

    private String posOf(String est) {
        return bank.get(est).map(Entry::pos).filter(p -> p != null && !p.isBlank()).orElse("other");
    }

    private static String sentenceTranslation(Word w, String sentence, boolean turkish) {
        if (w.getSentences() == null || sentence == null) return null;
        for (Word.Sentence s : w.getSentences()) {
            if (s.estonian() != null && s.estonian().trim().equalsIgnoreCase(sentence.trim())) {
                String t = turkish ? s.turkish() : s.english();
                return t == null || t.isBlank() ? null : t.trim();
            }
        }
        return null;
    }

    /** "slogan / parola" → {slogan, parola}: meanings that share any part are the same answer. */
    static Set<String> parts(String meaning) {
        Set<String> out = new HashSet<>();
        for (String p : meaning.toLowerCase(Locale.ROOT).split("[/,;()]")) {
            String t = p.trim().replaceFirst("^(to|a|an|the) ", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Shared ending and start, minus length difference: higher means the words look more alike. */
    static double similarity(String a, String b) {
        int suffix = 0;
        while (suffix < Math.min(a.length(), b.length())
            && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) suffix++;
        int prefix = 0;
        while (prefix < Math.min(a.length(), b.length()) && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        return suffix * 1.0 + prefix * 1.5 - Math.abs(a.length() - b.length()) * 0.5;
    }
}
