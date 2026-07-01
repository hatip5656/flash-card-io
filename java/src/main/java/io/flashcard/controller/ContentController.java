package io.flashcard.controller;

import io.flashcard.model.Idiom;
import io.flashcard.service.CategoryService;
import io.flashcard.service.IdiomService;
import io.flashcard.service.WordBankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ContentController {

    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");

    private final CategoryService categoryService;
    private final IdiomService idiomService;
    private final WordBankService wordBankService;

    public ContentController(CategoryService categoryService, IdiomService idiomService, WordBankService wordBankService) {
        this.categoryService = categoryService;
        this.idiomService = idiomService;
        this.wordBankService = wordBankService;
    }

    @GetMapping("/levels")
    public List<Map<String, Object>> getLevels() {
        return VALID_LEVELS.stream().sorted()
            .map(level -> Map.<String, Object>of(
                "level", level,
                "wordCount", wordBankService.getWordsForLevel(level).size()))
            .toList();
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> getCategories() {
        return categoryService.getAllCategories();
    }

    @GetMapping("/idioms")
    public List<Idiom> getIdioms() {
        return idiomService.getAllIdioms();
    }

    @GetMapping("/idioms/random")
    public Idiom getRandomIdiom() {
        return idiomService.getRandomIdiom();
    }
}
