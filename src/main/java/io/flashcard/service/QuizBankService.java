package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * The quiz's hand-checked material per word, read from {@code quiz/quiz-bank.json}: the word's type
 * (so wrong options are always words of the same type) and exam-style gap items built from the word's
 * example sentences. A gap item holds the sentence with the word's form cut out, three other forms of
 * the same word that do not fit there (from Ekilex's paradigm), and three other words in that same form
 * that do not fit either. Every item was checked by hand to have exactly one right answer.
 */
@Service
public class QuizBankService {

    private static final Logger log = LoggerFactory.getLogger(QuizBankService.class);
    static final String RESOURCE = "quiz/quiz-bank.json";

    /** {@code form} is Ekilex's morph code of the answer, e.g. IndPrSg1. */
    public record Item(String sentence, String gap, String answer, String form,
                       List<String> formOptions, List<String> wordOptions) {}

    /** {@code badMeaning} names the stored translations ("tr", "en") that are wrong or untranslated. */
    public record Entry(String estonian, String pos, List<Item> items, Set<String> badMeaning) {
        public Entry(String estonian, String pos, List<Item> items) { this(estonian, pos, items, Set.of()); }
    }

    record BankFile(List<Entry> words) {}

    private final Map<String, Entry> byWord;

    @Autowired
    public QuizBankService(ObjectMapper objectMapper) {
        this(load(objectMapper));
    }

    QuizBankService(Map<String, Entry> byWord) {
        this.byWord = Map.copyOf(byWord);
    }

    private static Map<String, Entry> load(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.warn("[quiz-bank] {} missing: quizzes fall back to meaning questions", RESOURCE);
            return Map.of();
        }
        try (InputStream in = resource.getInputStream()) {
            BankFile file = objectMapper.readValue(in, BankFile.class);
            Map<String, Entry> map = new HashMap<>();
            for (Entry e : file.words()) {
                map.put(e.estonian(), new Entry(e.estonian(), e.pos(),
                    e.items() == null ? List.of() : e.items(),
                    e.badMeaning() == null ? Set.of() : e.badMeaning()));
            }
            log.info("[quiz-bank] {} words, {} gap items", map.size(),
                map.values().stream().mapToInt(e -> e.items().size()).sum());
            return map;
        } catch (Exception e) {
            log.error("[quiz-bank] could not read {}: {}", RESOURCE, e.getMessage());
            return Map.of();
        }
    }

    public Optional<Entry> get(String estonian) {
        return Optional.ofNullable(byWord.get(estonian));
    }
}
