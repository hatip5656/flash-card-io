package io.flashcard.controller;

import io.flashcard.model.Idiom;
import io.flashcard.model.Word;
import io.flashcard.service.CategoryService;
import io.flashcard.service.IdiomService;
import io.flashcard.service.WordBankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private IdiomService idiomService;

    @MockitoBean
    private WordBankService wordBankService;

    @Test
    void getLevelsReturnsAllLevels() throws Exception {
        when(wordBankService.getWordsForLevel("A1")).thenReturn(List.of(makeWord("A1")));
        when(wordBankService.getWordsForLevel("A2")).thenReturn(List.of());
        when(wordBankService.getWordsForLevel("B1")).thenReturn(List.of(makeWord("B1"), makeWord("B1")));
        when(wordBankService.getWordsForLevel("B2")).thenReturn(List.of());

        mvc.perform(get("/api/levels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].level").exists())
            .andExpect(jsonPath("$[0].wordCount").exists());
    }

    @Test
    void getCategoriesReturnsCategories() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of(
            Map.of("key", "food", "label", "Food", "emoji", "apple", "wordCount", 20)
        ));

        mvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].label").value("Food"));
    }

    @Test
    void getIdiomsReturnsAllIdioms() throws Exception {
        when(idiomService.getAllIdioms()).thenReturn(List.of(
            new Idiom("Aeg on raha.", "Time is money.", "Don't waste time.")
        ));

        mvc.perform(get("/api/idioms"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].estonian").value("Aeg on raha."))
            .andExpect(jsonPath("$[0].english").value("Time is money."))
            .andExpect(jsonPath("$[0].meaning").value("Don't waste time."));
    }

    @Test
    void getRandomIdiomReturnsSingleIdiom() throws Exception {
        when(idiomService.getRandomIdiom()).thenReturn(
            new Idiom("Aeg on raha.", "Time is money.", "Don't waste time.")
        );

        mvc.perform(get("/api/idioms/random"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estonian").value("Aeg on raha."))
            .andExpect(jsonPath("$.english").exists())
            .andExpect(jsonPath("$.meaning").exists());
    }

    private Word makeWord(String level) {
        Word w = new Word();
        w.setId(level.toLowerCase() + "-test");
        w.setEstonian("test");
        w.setEnglish("test");
        w.setCefrLevel(level);
        w.setSentences(List.of());
        return w;
    }
}
