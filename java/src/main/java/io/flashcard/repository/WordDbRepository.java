package io.flashcard.repository;

import io.flashcard.model.Word;
import io.flashcard.model.GrammarStory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class WordDbRepository {

    private final JdbcTemplate jdbc;

    public WordDbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Word> loadAllWords() {
        List<Map<String, Object>> wordRows = jdbc.queryForList(
            "SELECT id, estonian, english, turkish, cefr_level, image_query FROM words ORDER BY id");
        List<Map<String, Object>> sentRows = jdbc.queryForList(
            "SELECT word_id, estonian, english, turkish FROM word_sentences ORDER BY word_id, sort_order");

        Map<String, List<Word.Sentence>> sentenceMap = new HashMap<>();
        for (Map<String, Object> r : sentRows) {
            String wordId = (String) r.get("word_id");
            sentenceMap.computeIfAbsent(wordId, k -> new ArrayList<>()).add(
                new Word.Sentence(
                    (String) r.get("estonian"),
                    (String) r.get("english"),
                    (String) r.get("turkish")));
        }

        List<Word> words = new ArrayList<>();
        for (Map<String, Object> r : wordRows) {
            Word w = new Word();
            w.setId((String) r.get("id"));
            w.setEstonian((String) r.get("estonian"));
            w.setEnglish((String) r.get("english"));
            w.setTurkish((String) r.get("turkish"));
            w.setCefrLevel((String) r.get("cefr_level"));
            w.setImageQuery((String) r.get("image_query"));
            w.setSentences(sentenceMap.getOrDefault(w.getId(), List.of()));
            words.add(w);
        }
        return words;
    }

    public List<GrammarStory> loadAllStories() {
        List<Map<String, Object>> storyRows = jdbc.queryForList(
            "SELECT id, cefr_level, topic, icon FROM grammar_stories ORDER BY id");
        List<Map<String, Object>> slideRows = jdbc.queryForList(
            """
            SELECT s.id AS slide_id, s.story_id, s.title, s.body, s.highlight, s.sort_order
            FROM grammar_story_slides s ORDER BY s.story_id, s.sort_order
            """);
        List<Map<String, Object>> exampleRows = jdbc.queryForList(
            """
            SELECT e.slide_id, e.estonian, e.english, e.turkish, e.sort_order
            FROM grammar_slide_examples e ORDER BY e.slide_id, e.sort_order
            """);

        Map<Integer, List<GrammarStory.Example>> exampleMap = new HashMap<>();
        for (Map<String, Object> r : exampleRows) {
            int slideId = ((Number) r.get("slide_id")).intValue();
            exampleMap.computeIfAbsent(slideId, k -> new ArrayList<>()).add(
                new GrammarStory.Example(
                    (String) r.get("estonian"),
                    (String) r.get("english"),
                    (String) r.get("turkish")));
        }

        Map<String, List<GrammarStory.Slide>> slideMap = new HashMap<>();
        for (Map<String, Object> r : slideRows) {
            String storyId = (String) r.get("story_id");
            int slideId = ((Number) r.get("slide_id")).intValue();
            slideMap.computeIfAbsent(storyId, k -> new ArrayList<>()).add(
                new GrammarStory.Slide(
                    (String) r.get("title"),
                    (String) r.get("body"),
                    (String) r.get("highlight"),
                    exampleMap.getOrDefault(slideId, List.of())));
        }

        List<GrammarStory> stories = new ArrayList<>();
        for (Map<String, Object> r : storyRows) {
            GrammarStory s = new GrammarStory();
            s.setId((String) r.get("id"));
            s.setCefrLevel((String) r.get("cefr_level"));
            s.setTopic((String) r.get("topic"));
            s.setIcon((String) r.get("icon"));
            s.setSlides(slideMap.getOrDefault(s.getId(), List.of()));
            stories.add(s);
        }
        return stories;
    }

    @Transactional
    public String addWord(String estonian, String english, String turkish, String cefrLevel,
                          List<Map<String, String>> sentences) {
        String id = cefrLevel.toLowerCase() + "-" + estonian.toLowerCase().replaceAll("\\s+", "-");

        List<String> existing = jdbc.queryForList(
            "SELECT id FROM words WHERE id = ? OR estonian = ?", String.class, id, estonian.toLowerCase());
        if (!existing.isEmpty()) return null;

        jdbc.update(
            "INSERT INTO words (id, estonian, english, turkish, cefr_level) VALUES (?, ?, ?, ?, ?)",
            id, estonian.toLowerCase(), english.toLowerCase(), turkish, cefrLevel);

        if (sentences != null) {
            for (int i = 0; i < sentences.size(); i++) {
                Map<String, String> s = sentences.get(i);
                jdbc.update(
                    "INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order) VALUES (?, ?, ?, ?, ?)",
                    id, s.get("estonian"), s.get("english"), s.get("turkish"), i);
            }
        }
        return id;
    }

    public Map<String, Object> getWordDetail(String id) {
        var wordRows = jdbc.queryForList(
            "SELECT id, estonian, english, turkish, cefr_level FROM words WHERE id = ?", id);
        if (wordRows.isEmpty()) return null;

        var sentences = jdbc.queryForList(
            "SELECT estonian, english, turkish, sort_order FROM word_sentences WHERE word_id = ? ORDER BY sort_order", id);

        Map<String, Object> result = new HashMap<>(wordRows.get(0));
        result.put("sentences", sentences);
        return result;
    }

    public List<Map<String, Object>> getUntranslated(String level, int limit) {
        if (level != null) {
            return jdbc.queryForList(
                "SELECT id, estonian, english, cefr_level FROM words WHERE turkish IS NULL AND cefr_level = ? ORDER BY cefr_level, estonian LIMIT ?",
                level, limit);
        }
        return jdbc.queryForList(
            "SELECT id, estonian, english, cefr_level FROM words WHERE turkish IS NULL ORDER BY cefr_level, estonian LIMIT ?",
            limit);
    }

    public int translateWord(String id, String turkish) {
        return jdbc.update(
            "UPDATE words SET turkish = ? WHERE id = ?", turkish, id);
    }

    public int translateSentence(String wordId, String estonian, String turkish) {
        return jdbc.update(
            "UPDATE word_sentences SET turkish = ? WHERE word_id = ? AND estonian = ?",
            turkish, wordId, estonian);
    }

    public List<Map<String, Object>> getWordStats() {
        return jdbc.queryForList("""
            SELECT cefr_level, COUNT(*) as total,
                   COUNT(turkish) as with_turkish,
                   COUNT(*) - COUNT(turkish) as missing_turkish
            FROM words GROUP BY cefr_level ORDER BY cefr_level
            """);
    }

    public int countSentences() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM word_sentences", Integer.class);
        return count != null ? count : 0;
    }

    public void updateImageCache(String wordId, String imageUrl, String photographer) {
        jdbc.update(
            "UPDATE words SET image_url = ?, image_photographer = ? WHERE id = ?",
            imageUrl, photographer, wordId);
    }

    public List<Map<String, Object>> getCachedImages(List<String> wordIds) {
        if (wordIds.isEmpty()) return List.of();
        return jdbc.queryForList(
            "SELECT id, image_url, image_photographer FROM words WHERE id = ANY(?)",
            (Object) wordIds.toArray(new String[0]));
    }

    public boolean wordExists(String estonian) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM words WHERE estonian = ?", Integer.class, estonian);
        return count != null && count > 0;
    }
}
