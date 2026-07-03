package io.flashcard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.model.GrammarLesson;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GrammarBankService {

    private static final Logger log = LoggerFactory.getLogger(GrammarBankService.class);

    private volatile List<GrammarLesson> lessons = List.of();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("data/grammar/lessons.json");
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                List<GrammarLesson> loaded = mapper.readValue(is, new TypeReference<>() {});
                this.lessons = List.copyOf(loaded);
                log.info("[grammar-bank] Loaded {} grammar lessons", loaded.size());
            }
        } catch (Exception e) {
            log.warn("[grammar-bank] No grammar lessons file found: {}", e.getMessage());
        }
    }

    public GrammarLesson getRandomLesson(String level, Set<String> sentIds) {
        List<GrammarLesson> available = lessons.stream()
            .filter(l -> l.cefrLevel().equals(level) && !sentIds.contains(l.id()))
            .toList();

        if (available.isEmpty()) {
            List<GrammarLesson> all = lessons.stream()
                .filter(l -> l.cefrLevel().equals(level))
                .toList();
            if (all.isEmpty()) return null;
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    public List<GrammarLesson> getAllLessons() {
        return lessons;
    }
}
