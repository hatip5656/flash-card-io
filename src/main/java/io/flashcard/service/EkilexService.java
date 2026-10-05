package io.flashcard.service;

import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

@Service
public class EkilexService {

    private static final Logger log = LoggerFactory.getLogger(EkilexService.class);
    private static final String API_BASE = "https://ekilex.ee/api";
    private static final int MAX_DETAIL_LOOKUPS = 10;
    private static final String LANG_EST = "est";
    private static final String LANG_ENG = "eng";
    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");
    // Two-letter prefixes for systematic vocabulary crawling
    private static final String[] VOWELS = {"a", "e", "i", "o", "u", "õ", "ä", "ö", "ü"};
    private static final String[] CONSONANTS = {"h", "j", "k", "l", "m", "n", "p", "r", "s", "t", "v"};
    private static final String[] SEARCH_PATTERNS;
    static {
        List<String> patterns = new ArrayList<>();
        // Consonant + vowel (most common Estonian word starts: ka, ke, ki, ko, ku, la, le...)
        for (String c : CONSONANTS) for (String v : VOWELS) patterns.add(c + v + "*");
        // Vowel + consonant (al, an, ar, el, en, er, il...)
        for (String v : VOWELS) for (String c : CONSONANTS) patterns.add(v + c + "*");
        // Vowel + vowel (aa, ea, ai, au, ei, oi, ui, õi...)
        for (String v1 : VOWELS) for (String v2 : VOWELS) patterns.add(v1 + v2 + "*");
        SEARCH_PATTERNS = patterns.toArray(new String[0]);
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(30, 60_000);
    private final CircuitBreaker circuitBreaker;

    public EkilexService(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.circuitBreaker = CircuitBreaker.of("ekilex", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(3)
            .waitDurationInOpenState(Duration.ofMinutes(3))
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build());
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.info("[ekilex] Circuit breaker: {}", event));
    }

    public record EkilexWord(int wordId, String wordValue, String cefrLevel, String english,
                             String pos, List<Usage> usages) {}
    public record Usage(String estonian, String english) {}

    private JsonNode apiRequest(String path, String apiKey) {
        if (!rateLimiter.tryConsume()) {
            log.warn("[ekilex] Rate limited, skipping {}", path);
            return null;
        }
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + path))
                        .header("ekilex-api-key", apiKey)
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("API error " + response.statusCode() + " for " + path);
                    }
                    return objectMapper.readTree(response.body());
                } catch (RuntimeException e) { throw e; }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        } catch (CallNotPermittedException e) {
            log.debug("[ekilex] Circuit open, skipping {}", path);
            return null;
        } catch (Exception e) {
            log.error("[ekilex] Request failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    private String extractEnglish(JsonNode lexeme) {
        JsonNode groups = lexeme.path("synonymLangGroups");
        if (groups.isArray()) {
            for (JsonNode group : groups) {
                if (LANG_ENG.equals(group.path("lang").asText())) {
                    for (JsonNode syn : group.path("synonyms")) {
                        JsonNode words = syn.path("words");
                        if (words.isArray() && !words.isEmpty()) {
                            return words.get(0).path("wordValue").asText(null);
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<Usage> extractUsages(JsonNode lexeme) {
        List<Usage> results = new ArrayList<>();
        JsonNode usages = lexeme.path("usages");
        if (!usages.isArray()) return results;
        for (JsonNode usage : usages) {
            String est = usage.has("value") ? usage.path("value").asText("") : usage.path("valuePrese").asText("");
            est = est.replaceAll("<[^>]*>", "");
            if (est.isBlank()) continue;
            String eng = "";
            for (JsonNode t : usage.path("translations")) {
                if (LANG_ENG.equals(t.path("lang").asText())) {
                    eng = t.path("value").asText("").replaceAll("<[^>]*>", "");
                    break;
                }
            }
            results.add(new Usage(est, eng));
        }
        return results;
    }

    private String extractPos(JsonNode lexeme) {
        JsonNode pos = lexeme.path("pos");
        return pos.isArray() && !pos.isEmpty() ? pos.get(0).path("value").asText(null) : null;
    }

    public List<EkilexWord> searchWord(String word, String apiKey) {
        JsonNode data = apiRequest("/word/search/" + URLEncoder.encode(word, StandardCharsets.UTF_8), apiKey);
        if (data == null || !data.has("words")) return List.of();

        List<EkilexWord> results = new ArrayList<>();
        for (JsonNode w : data.path("words")) {
            if (!LANG_EST.equals(w.path("lang").asText())) continue;
            int wordId = w.path("wordId").asInt();
            String wordValue = w.path("wordValue").asText();

            JsonNode details = apiRequest("/word/details/" + wordId, apiKey);
            if (details == null) continue;

            for (JsonNode lexeme : details.path("lexemes")) {
                String cefrLevel = lexeme.path("lexemeProficiencyLevelCode").asText(null);
                if (cefrLevel == null || !VALID_LEVELS.contains(cefrLevel)) continue;
                String english = extractEnglish(lexeme);
                if (english == null) continue;
                String pos = extractPos(lexeme);
                List<Usage> usages = extractUsages(lexeme);

                results.add(new EkilexWord(wordId, wordValue, cefrLevel, english.toLowerCase(), pos,
                    usages.size() > 3 ? usages.subList(0, 3) : usages));
            }
        }
        return results;
    }

    /**
     * Search for a random word at the given CEFR level using a specific pattern.
     * @param excludeWords combined set of words to skip (existing words + candidates)
     */
    public EkilexWord getRandomWordForLevel(String level, Set<String> excludeWords, String apiKey) {
        return getRandomWordForLevel(level, excludeWords, apiKey, null, Set.of());
    }

    /**
     * Search for words at a given CEFR level, skipping already-checked Ekilex IDs.
     * Returns the first matching word, or null. Also populates checkedOut with
     * all word IDs that were detail-looked-up (for caching by caller).
     */
    public record LookupResult(EkilexWord word, List<CheckedWord> checked) {}
    public record CheckedWord(int wordId, String wordValue, boolean hasCefr) {}

    public LookupResult searchForLevel(String level, Set<String> excludeWords, String apiKey,
                                        String pattern, Set<Integer> alreadyCheckedIds) {
        JsonNode data = apiRequest("/word/search/" + URLEncoder.encode(pattern, StandardCharsets.UTF_8), apiKey);
        if (data == null || !data.has("words")) return new LookupResult(null, List.of());

        // Pre-filter: Estonian, single word, not excluded, not already checked
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode w : data.path("words")) {
            if (!LANG_EST.equals(w.path("lang").asText())) continue;
            String wordValue = w.path("wordValue").asText("").toLowerCase();
            if (wordValue.isBlank() || wordValue.contains(" ")) continue;
            if (excludeWords.contains(wordValue)) continue;
            int wordId = w.path("wordId").asInt();
            if (alreadyCheckedIds.contains(wordId)) continue;
            candidates.add(w);
        }

        if (candidates.isEmpty()) return new LookupResult(null, List.of());
        Collections.shuffle(candidates);

        List<CheckedWord> checkedWords = new ArrayList<>();
        int lookups = 0;
        for (JsonNode w : candidates) {
            if (++lookups > MAX_DETAIL_LOOKUPS) break;

            int wordId = w.path("wordId").asInt();
            String wordValue = w.path("wordValue").asText();
            JsonNode details = apiRequest("/word/details/" + wordId, apiKey);
            if (details == null) continue;

            boolean foundCefr = false;
            for (JsonNode lexeme : details.path("lexemes")) {
                String cefrLevel = lexeme.path("lexemeProficiencyLevelCode").asText(null);
                if (cefrLevel != null && VALID_LEVELS.contains(cefrLevel)) {
                    foundCefr = true;
                    if (level.equals(cefrLevel)) {
                        String english = extractEnglish(lexeme);
                        if (english == null) continue;
                        String pos = extractPos(lexeme);
                        List<Usage> usages = extractUsages(lexeme);
                        checkedWords.add(new CheckedWord(wordId, wordValue, true));
                        EkilexWord result = new EkilexWord(wordId, wordValue, cefrLevel,
                            english.toLowerCase(), pos, usages.size() > 3 ? usages.subList(0, 3) : usages);
                        return new LookupResult(result, checkedWords);
                    }
                }
            }
            checkedWords.add(new CheckedWord(wordId, wordValue, foundCefr));
        }
        return new LookupResult(null, checkedWords);
    }

    /** Legacy method for compatibility */
    public EkilexWord getRandomWordForLevel(String level, Set<String> excludeWords, String apiKey,
                                             String patternOverride, Set<Integer> alreadyCheckedIds) {
        String pattern = patternOverride != null ? patternOverride
            : SEARCH_PATTERNS[ThreadLocalRandom.current().nextInt(SEARCH_PATTERNS.length)];
        var result = searchForLevel(level, excludeWords, apiKey, pattern, alreadyCheckedIds);
        return result.word();
    }

    /** Returns the total number of available search patterns */
    public int getPatternCount() {
        return SEARCH_PATTERNS.length;
    }

    /** Returns a specific search pattern by index */
    public String getPattern(int index) {
        return SEARCH_PATTERNS[index % SEARCH_PATTERNS.length];
    }
}
