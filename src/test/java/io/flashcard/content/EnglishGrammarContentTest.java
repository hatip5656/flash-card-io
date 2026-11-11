package io.flashcard.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the English grammar lessons and podcast scripts in changelog 043 before they reach learners:
 * one script per lesson, speakable by the TTS service, built like the Turkish scripts, and no lesson text
 * or English narration that compares Estonian with Turkish.
 */
class EnglishGrammarContentTest {

    private static final String CHANGELOG = "db/changelog/043-english-grammar-content.sql";
    private static final Pattern SCRIPT = Pattern.compile(
        "VALUES \\('([a-z0-9-]+)', 'en', \\$s\\$(.*?)\\$s\\$::jsonb", Pattern.DOTALL);
    private static final Pattern CONTENT = Pattern.compile(
        "SET content = \\$c\\$(.*?)\\$c\\$ WHERE id = '([a-z0-9-]+)'", Pattern.DOTALL);
    // A comparison says "Turkish" (or Türkçe…); the country name in an example translation is fine
    private static final Pattern TURKISH = Pattern.compile("(?i)turkish|türk");
    // Characters the TTS would read out or choke on (<< >> markers are checked separately)
    private static final Pattern UNSPEAKABLE = Pattern.compile("[&/*#_\\[\\]{}|=+@%$]");

    private static String sql;
    private static Map<String, List<Map<String, Object>>> scripts;
    private static Map<String, String> contents;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws Exception {
        try (InputStream in = EnglishGrammarContentTest.class.getClassLoader().getResourceAsStream(CHANGELOG)) {
            assertNotNull(in, CHANGELOG + " is missing");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var mapper = new ObjectMapper();
        scripts = new LinkedHashMap<>();
        Matcher m = SCRIPT.matcher(sql);
        while (m.find()) {
            assertNull(scripts.put(m.group(1), mapper.readValue(m.group(2), List.class)), "two scripts for " + m.group(1));
        }
        contents = new LinkedHashMap<>();
        m = CONTENT.matcher(sql);
        while (m.find()) assertNull(contents.put(m.group(2), m.group(1)), "two contents for " + m.group(2));
    }

    @Test
    void everyLessonHasOneEnglishScript() {
        assertEquals(53, scripts.size(), "lessons with an English script");
    }

    @Test
    void scriptsAreBuiltLikeTheTurkishOnes() {
        List<String> problems = new ArrayList<>();
        scripts.forEach((id, segments) -> {
            if (segments.size() < 15 || segments.size() > 25) problems.add(id + ": " + segments.size() + " segments");
            long et = segments.stream().filter(s -> "et".equals(s.get("language"))).count();
            if (et < 5) problems.add(id + ": only " + et + " Estonian segments");
            if (!"en".equals(segments.getFirst().get("language"))) problems.add(id + ": does not open in English");
            if (!"en".equals(segments.getLast().get("language"))) problems.add(id + ": does not close in English");
            for (var s : segments) {
                if (!s.keySet().equals(new HashSet<>(List.of("text", "language", "pause_after_ms")))) {
                    problems.add(id + ": segment keys " + s.keySet());
                }
                if (!(s.get("pause_after_ms") instanceof Integer p) || p < 700 || p > 3800) {
                    problems.add(id + ": pause " + s.get("pause_after_ms"));
                }
                if (!List.of("en", "et").contains(s.get("language"))) problems.add(id + ": language " + s.get("language"));
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void scriptsAreSpeakable() {
        List<String> problems = new ArrayList<>();
        scripts.forEach((id, segments) -> {
            for (var s : segments) {
                String text = String.valueOf(s.get("text"));
                if (text.isBlank()) problems.add(id + ": empty segment");
                if (text.matches("(?s).*\\d.*")) problems.add(id + ": digits in \"" + text + "\"");
                if (UNSPEAKABLE.matcher(text).find()) problems.add(id + ": symbol in \"" + text + "\"");
                if ("et".equals(s.get("language"))) {
                    if (text.contains("<<") || text.contains(">>")) problems.add(id + ": marker in Estonian \"" + text + "\"");
                } else {
                    String rest = text.replaceAll("<<[^<>]+>>", "");
                    if (rest.contains("<") || rest.contains(">")) problems.add(id + ": unbalanced marker in \"" + text + "\"");
                }
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void englishNeverComparesWithTurkish() {
        List<String> problems = new ArrayList<>();
        contents.forEach((id, html) -> { if (TURKISH.matcher(html).find()) problems.add(id + ": lesson text"); });
        scripts.forEach((id, segments) -> segments.stream()
            .filter(s -> "en".equals(s.get("language")) && TURKISH.matcher(String.valueOf(s.get("text"))).find())
            .forEach(s -> problems.add(id + ": \"" + s.get("text") + "\"")));
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyRewrittenLessonHasAScript() {
        assertTrue(scripts.keySet().containsAll(contents.keySet()), "content for a lesson without a script");
        assertTrue(contents.size() >= 52, "rewritten lessons: " + contents.size());
    }
}
