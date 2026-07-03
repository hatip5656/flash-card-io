package io.flashcard.service;

import io.flashcard.model.GrammarStory;
import io.flashcard.repository.WordDbRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class StoryBankService {

    private static final Logger log = LoggerFactory.getLogger(StoryBankService.class);

    private final WordDbRepository wordDbRepository;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile List<GrammarStory> stories = List.of();

    public StoryBankService(WordDbRepository wordDbRepository) {
        this.wordDbRepository = wordDbRepository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        List<GrammarStory> loaded = wordDbRepository.loadAllStories();
        lock.writeLock().lock();
        try {
            this.stories = List.copyOf(loaded);
        } finally {
            lock.writeLock().unlock();
        }
        log.info("[story-bank] Loaded {} grammar stories from database", loaded.size());
    }

    public List<GrammarStory> getAllStories() {
        lock.readLock().lock();
        try {
            return stories;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<GrammarStory> getStoriesForLevel(String level) {
        lock.readLock().lock();
        try {
            return stories.stream().filter(s -> s.getCefrLevel().equals(level)).toList();
        } finally {
            lock.readLock().unlock();
        }
    }
}
