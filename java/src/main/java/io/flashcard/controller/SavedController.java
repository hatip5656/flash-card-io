package io.flashcard.controller;

import io.flashcard.model.Word;
import io.flashcard.repository.SavedWordRepository;
import io.flashcard.service.WordBankService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/saved")
public class SavedController {

    private final SavedWordRepository savedWordRepo;
    private final WordBankService wordBankService;

    public SavedController(SavedWordRepository savedWordRepo, WordBankService wordBankService) {
        this.savedWordRepo = savedWordRepo;
        this.wordBankService = wordBankService;
    }

    @GetMapping
    public List<Map<String, Object>> getSavedWords(HttpServletRequest request) {
        long chatId = getUserId(request);
        List<String> wordIds = savedWordRepo.getSavedWordIds(chatId);
        return wordIds.stream()
            .map(wordBankService::getWordById)
            .filter(w -> w != null)
            .map(w -> Map.<String, Object>of(
                "id", w.getId(),
                "estonian", w.getEstonian(),
                "english", w.getEnglish(),
                "turkish", w.getTurkish() != null ? w.getTurkish() : "",
                "cefrLevel", w.getCefrLevel(),
                "sentences", w.getSentences()))
            .toList();
    }

    @PostMapping("/{wordId}")
    public Map<String, Object> addSavedWord(HttpServletRequest request, @PathVariable String wordId) {
        savedWordRepo.saveWord(getUserId(request), wordId);
        return Map.of("saved", true, "wordId", wordId);
    }

    @DeleteMapping("/{wordId}")
    public Map<String, Object> removeSavedWord(HttpServletRequest request, @PathVariable String wordId) {
        savedWordRepo.unsaveWord(getUserId(request), wordId);
        return Map.of("saved", false, "wordId", wordId);
    }
}
