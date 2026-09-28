package io.flashcard.service;

import io.flashcard.model.Word;
import io.flashcard.repository.WordDbRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class WordBankService {

    private static final Logger log = LoggerFactory.getLogger(WordBankService.class);

    private final WordDbRepository wordDbRepository;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile List<Word> words = List.of();
    private volatile Map<String, Word> wordMap = Map.of();

    public WordBankService(WordDbRepository wordDbRepository) {
        this.wordDbRepository = wordDbRepository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        List<Word> loaded = wordDbRepository.loadAllWords();
        Map<String, Word> map = loaded.stream().collect(Collectors.toMap(Word::getId, w -> w));
        lock.writeLock().lock();
        try {
            this.words = List.copyOf(loaded);
            this.wordMap = Map.copyOf(map);
        } finally {
            lock.writeLock().unlock();
        }
        log.info("[word-bank] Loaded {} words from database", loaded.size());
    }

    public List<Word> getAllWords() {
        lock.readLock().lock();
        try {
            return words;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Word> getWordsForLevel(String level) {
        lock.readLock().lock();
        try {
            return words.stream().filter(w -> w.getCefrLevel().equals(level)).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Word getWordById(String id) {
        lock.readLock().lock();
        try {
            return wordMap.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Word> getUnsent(String level, Collection<String> sentIds) {
        Set<String> sent = new HashSet<>(sentIds);
        lock.readLock().lock();
        try {
            return words.stream()
                .filter(w -> w.getCefrLevel().equals(level) && !sent.contains(w.getId()))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get unseen words up to and including the given level.
     * Deterministic order: current level first, then lower levels descending.
     * Within each level, words appear in stable DB order.
     */
    public List<Word> getUnsentUpToLevel(String level, Collection<String> sentIds) {
        Set<String> sent = new HashSet<>(sentIds);
        List<String> levelOrder = List.of("A1", "A2", "B1", "B2");
        int maxIdx = levelOrder.indexOf(level);
        if (maxIdx < 0) maxIdx = 0;

        // Current level first, then lower levels descending (B1, A2, A1)
        List<String> orderedLevels = new ArrayList<>();
        orderedLevels.add(level);
        for (int i = maxIdx - 1; i >= 0; i--) {
            orderedLevels.add(levelOrder.get(i));
        }

        lock.readLock().lock();
        try {
            List<Word> result = new ArrayList<>();
            for (String l : orderedLevels) {
                words.stream()
                    .filter(w -> w.getCefrLevel().equals(l) && !sent.contains(w.getId()))
                    .forEach(result::add);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

}
