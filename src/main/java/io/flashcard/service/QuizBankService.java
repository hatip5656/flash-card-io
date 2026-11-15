package io.flashcard.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The quiz's checked material per word, from the database: the word's type ({@code words.pos}, so wrong
 * options are always words of the same type), which stored meanings are unfit as answers
 * ({@code words.quiz_bad_meaning}), and exam-style gap items ({@code word_quiz_items}).
 *
 * <p>A gap item points at one of the word's example sentences and names the word's form in it, three
 * other forms of the same word that don't fit there (from Ekilex's paradigm) and three other words in that
 * form that don't fit either. The gap is cut from the sentence's current text when loading: an item whose
 * sentence was edited and no longer holds the answer is left out.
 */
@Service
public class QuizBankService {

    private static final Logger log = LoggerFactory.getLogger(QuizBankService.class);

    /** {@code form} is Ekilex's morph code of the answer, e.g. IndPrSg1. */
    public record Item(String sentence, String gap, String answer, String form,
                       List<String> formOptions, List<String> wordOptions) {}

    /** {@code badMeaning} names the stored meanings ("tr", "en") that are wrong or untranslated. */
    public record Entry(String estonian, String pos, List<Item> items, Set<String> badMeaning) {
        public Entry(String estonian, String pos, List<Item> items) { this(estonian, pos, items, Set.of()); }
    }

    private final JdbcTemplate jdbc;
    private volatile Map<String, Entry> byWord;

    @Autowired
    public QuizBankService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.byWord = Map.of();
    }

    QuizBankService(Map<String, Entry> byWord) {
        this.jdbc = null;
        this.byWord = Map.copyOf(byWord);
    }

    /** Loaded at start and every 10 minutes, so edits made in the admin reach the quiz. */
    @PostConstruct
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void reload() {
        if (jdbc == null) return;
        try {
            List<Map<String, Object>> words = jdbc.queryForList(
                "SELECT estonian, pos, quiz_bad_meaning FROM words WHERE pos IS NOT NULL");
            List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT w.estonian, s.estonian AS sentence, i.answer, i.morph_code, i.form_options, i.word_options
                FROM word_quiz_items i
                JOIN words w ON w.id = i.word_id
                JOIN word_sentences s ON s.id = i.sentence_id
                ORDER BY i.id
                """);
            Map<String, Entry> loaded = fromRows(words, items);
            byWord = Map.copyOf(loaded);
            log.info("[quiz-bank] {} words, {} gap items", loaded.size(),
                loaded.values().stream().mapToInt(e -> e.items().size()).sum());
        } catch (Exception e) {
            log.error("[quiz-bank] could not load: {}", e.getMessage());
        }
    }

    static Map<String, Entry> fromRows(List<Map<String, Object>> words, List<Map<String, Object>> items) {
        Map<String, List<Item>> itemsByWord = new HashMap<>();
        for (Map<String, Object> r : items) {
            String sentence = (String) r.get("sentence");
            String answer = (String) r.get("answer");
            if (sentence == null || answer == null) continue;
            String gap = cutGap(sentence, answer);
            if (gap == null) continue;
            itemsByWord.computeIfAbsent((String) r.get("estonian"), k -> new ArrayList<>()).add(new Item(
                sentence, gap, answer, (String) r.get("morph_code"),
                strings(r.get("form_options")), strings(r.get("word_options"))));
        }
        Map<String, Entry> out = new HashMap<>();
        for (Map<String, Object> r : words) {
            String est = (String) r.get("estonian");
            out.put(est, new Entry(est, (String) r.get("pos"),
                List.copyOf(itemsByWord.getOrDefault(est, List.of())),
                Set.copyOf(strings(r.get("quiz_bad_meaning")))));
        }
        return out;
    }

    /** The sentence with its one whole-word occurrence of {@code answer} replaced by ___, or null. */
    static String cutGap(String sentence, String answer) {
        Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(answer) + "(?![\\p{L}\\p{N}])").matcher(sentence);
        if (!m.find()) return null;
        int start = m.start(), end = m.end();
        if (m.find()) return null; // twice: which one is the gap would be unclear
        return sentence.substring(0, start) + "___" + sentence.substring(end);
    }

    private static List<String> strings(Object value) {
        try {
            Object array = value instanceof java.sql.Array a ? a.getArray() : value;
            if (array instanceof String[] s) return List.of(s);
        } catch (java.sql.SQLException e) {
            log.warn("[quiz-bank] unreadable array: {}", e.getMessage());
        }
        return List.of();
    }

    public Optional<Entry> get(String estonian) {
        return Optional.ofNullable(byWord.get(estonian));
    }
}
