package io.flashcard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private volatile Map<String, CategoryData> categories = Map.of();

    public record CategoryData(String label, String emoji, List<String> words) {}

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("data/categories.json");
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, CategoryData> loaded = mapper.readValue(is, new TypeReference<>() {});
                this.categories = Map.copyOf(loaded);
                log.info("[categories] Loaded {} categories", loaded.size());
            }
        } catch (Exception e) {
            log.warn("[categories] No categories file found: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getAllCategories() {
        return categories.entrySet().stream()
            .map(e -> Map.<String, Object>of(
                "key", e.getKey(),
                "label", e.getValue().label(),
                "emoji", e.getValue().emoji(),
                "wordCount", e.getValue().words().size()))
            .toList();
    }
}
