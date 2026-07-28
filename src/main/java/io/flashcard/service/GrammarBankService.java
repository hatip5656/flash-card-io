package io.flashcard.service;

import io.flashcard.model.GrammarLesson;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GrammarBankService {

    private static final Logger log = LoggerFactory.getLogger(GrammarBankService.class);

    private final JdbcTemplate jdbc;
    private volatile List<GrammarLesson> lessons = List.of();

    public GrammarBankService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public void reload() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, cefr_level, topic, topic_tr, content, content_tr FROM grammar_lessons ORDER BY cefr_level, id");
            this.lessons = rows.stream().map(r -> new GrammarLesson(
                (String) r.get("id"),
                (String) r.get("cefr_level"),
                (String) r.get("topic"),
                (String) r.get("topic_tr"),
                (String) r.get("content"),
                (String) r.get("content_tr")
            )).toList();
            log.info("[grammar-bank] Loaded {} grammar lessons from database", lessons.size());
        } catch (Exception e) {
            log.warn("[grammar-bank] Failed to load from DB: {}", e.getMessage());
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
