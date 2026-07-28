package io.flashcard.service;

import io.flashcard.model.Word;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SentenceService {

    private final TatoebaService tatoebaService;

    public SentenceService(TatoebaService tatoebaService) {
        this.tatoebaService = tatoebaService;
    }

    public record Sentence(String estonian, String english, String turkish) {}

    public Sentence resolveSentence(Word word) {
        // Try Tatoeba first
        var tatoeba = tatoebaService.searchSentences(word.getEstonian(), 5);
        if (!tatoeba.isEmpty()) {
            var pick = tatoeba.get(ThreadLocalRandom.current().nextInt(tatoeba.size()));
            return new Sentence(pick.estonian(), pick.english(), null);
        }

        // Fallback to bundled sentences
        List<Word.Sentence> sentences = word.getSentences();
        if (sentences != null && !sentences.isEmpty()) {
            var pick = sentences.get(ThreadLocalRandom.current().nextInt(sentences.size()));
            return new Sentence(pick.estonian(), pick.english(), pick.turkish());
        }

        // Last resort
        return new Sentence(word.getEstonian(), word.getEnglish(), word.getTurkish());
    }
}
