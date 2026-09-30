package io.flashcard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import io.flashcard.model.Podcast;
import io.flashcard.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PodcastService {

    private static final Logger log = LoggerFactory.getLogger(PodcastService.class);

    private static final String PODCAST_PROMPT = """
            You are creating a daily language learning podcast episode for an Estonian language learner.

            LEARNER PROFILE:
            %s

            Generate a podcast script as a JSON array of segments. Each segment object has:
            - "text": the spoken text (string)
            - "language": "en" or "et"
            - "pause_after_ms": pause in milliseconds after this segment (integer)

            STRUCTURE:
            1. Greeting (EN) — warm, reference their streak or recent progress
            2. Today's Focus (EN) — introduce 3-5 words from their weak/missed list
            3. For EACH word:
               a. Introduce the word (EN): "Our next word is..."
               b. Say the Estonian word (ET): clear pronunciation
               c. Pause 2000ms for the listener to repeat
               d. Use it in a sentence (ET): natural sentence with the word
               e. Translate (EN): explain the sentence meaning
               f. Pause 1000ms
            4. Quick Review (mixed EN/ET): rapid-fire all today's words with short pauses
            5. Grammar Mini-Lesson (EN+ET): one pattern relevant to the words covered
            6. Closing (EN): encouragement, summary

            RULES:
            - Target 15-25 segments total
            - Estonian text: spell out ALL numbers (e.g., "kaks" not "2")
            - Estonian text: keep sentences under 15 words
            - English text: keep sentences under 20 words
            - Adjust complexity to the learner's CEFR level
            - Be encouraging and personal
            - Do NOT wrap the JSON in markdown code fences
            - Return ONLY the JSON array, no other text
            """;

    private final GeminiService geminiService;
    private final PodcastRepository podcastRepo;
    private final SentWordRepository sentWordRepo;
    private final SubscriberRepository subscriberRepo;
    private final QuizRepository quizRepo;
    private final ActivityRepository activityRepo;
    private final WordBankService wordBankService;
    private final DiskCacheService diskCache;
    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PodcastService(GeminiService geminiService, PodcastRepository podcastRepo,
                          SentWordRepository sentWordRepo, SubscriberRepository subscriberRepo,
                          QuizRepository quizRepo, ActivityRepository activityRepo,
                          WordBankService wordBankService, DiskCacheService diskCache,
                          AppProperties appProperties, HttpClient httpClient,
                          ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.podcastRepo = podcastRepo;
        this.sentWordRepo = sentWordRepo;
        this.subscriberRepo = subscriberRepo;
        this.quizRepo = quizRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
        this.diskCache = diskCache;
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String requestGeneration(long chatId) {
        if (!geminiService.isAvailable()) {
            throw new IllegalStateException("AI service not available");
        }
        if (podcastRepo.hasGeneratingPodcast(chatId)) {
            throw new IllegalStateException("A podcast is already being generated");
        }

        String level = subscriberRepo.getSubscriberLevel(chatId);

        Podcast podcast = new Podcast();
        podcast.setChatId(chatId);
        podcast.setTitle("Daily Estonian Lesson");
        podcast.setCefrLevel(level);
        podcast.setDescription("Personalized lesson based on your learning progress");

        String podcastId = podcastRepo.create(podcast);

        Thread.startVirtualThread(() -> generateAsync(podcastId, chatId, level));

        return podcastId;
    }

    private void generateAsync(String podcastId, long chatId, String level) {
        try {
            podcastRepo.updateStatus(podcastId, "generating", null);

            // 1. Build learner context
            String learnerContext = buildLearnerContext(chatId, level);

            // 2. Generate script via Gemini (8192 tokens for full podcast script)
            log.info("[podcast] Generating script for user {} ({})", chatId, level);
            String prompt = String.format(PODCAST_PROMPT, learnerContext);
            String scriptJson = geminiService.chat(prompt, List.of(), "", 8192);

            if (scriptJson == null || scriptJson.isBlank()) {
                podcastRepo.updateStatus(podcastId, "failed", "AI did not generate a script");
                return;
            }

            // Clean up response: strip markdown fences if present
            scriptJson = scriptJson.strip();
            if (scriptJson.startsWith("```")) {
                scriptJson = scriptJson.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
            }

            // Repair truncated JSON: if it doesn't end with ], try to close it
            scriptJson = repairTruncatedJson(scriptJson);

            // Validate JSON
            List<?> segments = objectMapper.readValue(scriptJson, List.class);
            if (segments.isEmpty()) {
                podcastRepo.updateStatus(podcastId, "failed", "Script has no segments");
                return;
            }

            // 3. Call TTS service to synthesize
            log.info("[podcast] Synthesizing {} segments for podcast {}", segments.size(), podcastId);
            byte[] audio = callTtsPodcastApi(scriptJson);
            if (audio == null) {
                podcastRepo.updateStatus(podcastId, "failed", "TTS synthesis failed");
                return;
            }

            // 4. Cache audio and update record
            String cacheKey = "podcast\0" + podcastId;
            diskCache.setCachedBuffer("podcast", cacheKey, "wav", audio);

            int durationSeconds = estimateDuration(audio);
            podcastRepo.updateReady(podcastId, scriptJson, cacheKey, durationSeconds);

            log.info("[podcast] Podcast {} ready ({} segments, ~{}s)", podcastId, segments.size(), durationSeconds);

        } catch (Exception e) {
            log.error("[podcast] Generation failed for {}: {}", podcastId, e.getMessage(), e);
            podcastRepo.updateStatus(podcastId, "failed", e.getMessage());
        }
    }

    private byte[] callTtsPodcastApi(String scriptJson) throws JsonProcessingException {
        // Parse segments from Gemini output and wrap in podcast request
        List<?> segments = objectMapper.readValue(scriptJson, List.class);
        Map<String, Object> request = Map.of(
            "segments", segments,
            "output_sample_rate", 24000
        );

        String body = objectMapper.writeValueAsString(request);

        try {
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/v1/podcast/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<byte[]> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("[podcast] TTS API returned {}", response.statusCode());
                return null;
            }

            return response.body();
        } catch (Exception e) {
            log.error("[podcast] TTS API call failed: {}", e.getMessage());
            return null;
        }
    }

    private String repairTruncatedJson(String json) {
        if (json.endsWith("]")) return json;

        // Find the last complete object (ends with })
        int lastBrace = json.lastIndexOf('}');
        if (lastBrace <= 0) return json;

        // Truncate to last complete object and close the array
        String repaired = json.substring(0, lastBrace + 1).strip();
        // Remove trailing comma if present
        if (repaired.endsWith(",")) {
            repaired = repaired.substring(0, repaired.length() - 1);
        }
        repaired += "]";

        log.warn("[podcast] Repaired truncated JSON (cut at position {})", lastBrace);
        return repaired;
    }

    private int estimateDuration(byte[] wavData) {
        // WAV header: sample rate at offset 24 (4 bytes LE), data size at offset 40 (4 bytes LE)
        if (wavData.length < 44) return 0;
        int sampleRate = (wavData[24] & 0xFF) | ((wavData[25] & 0xFF) << 8)
                       | ((wavData[26] & 0xFF) << 16) | ((wavData[27] & 0xFF) << 24);
        int dataSize = (wavData[40] & 0xFF) | ((wavData[41] & 0xFF) << 8)
                     | ((wavData[42] & 0xFF) << 16) | ((wavData[43] & 0xFF) << 24);
        if (sampleRate <= 0) return 0;
        return dataSize / (sampleRate * 2); // 16-bit mono
    }

    public Optional<Podcast> getStatus(String podcastId) {
        return podcastRepo.findById(podcastId);
    }

    public byte[] getAudio(String podcastId) {
        return podcastRepo.findById(podcastId)
            .filter(p -> "ready".equals(p.getStatus()))
            .map(p -> diskCache.getCachedBuffer("podcast", p.getAudioCacheKey(), "wav"))
            .orElse(null);
    }

    public Optional<Podcast> getLatest(long chatId) {
        return podcastRepo.findLatestReady(chatId);
    }

    public List<Podcast> getHistory(long chatId, int limit) {
        return podcastRepo.findHistory(chatId, limit);
    }

    private String buildLearnerContext(long chatId, String level) {
        var prefs = subscriberRepo.getPreferences(chatId);
        String nativeLang = prefs != null ? prefs.getNativeLanguage() : "english";

        int streak = activityRepo.getStreak(chatId);
        var wordCounts = sentWordRepo.getWordCounts(chatId);
        var quizStats = quizRepo.getQuizStats(chatId);

        int seen = ((Number) wordCounts.get("seen")).intValue();
        int mastered = ((Number) wordCounts.get("mastered")).intValue();
        int totalQuizzes = ((Number) quizStats.get("total")).intValue();
        int avgPct = (int) Math.round(((Number) quizStats.get("avg_pct")).doubleValue());

        var weakWords = sentWordRepo.getWeakWords(chatId, 10);
        String weakList = weakWords.stream()
            .map(w -> "\"" + w.get("word_value") + "\" (" + w.get("english") + ")")
            .collect(Collectors.joining(", "));

        var missedWords = quizRepo.getMostMissedWords(chatId, 8);
        String missedList = missedWords.stream()
            .map(w -> "\"" + w.get("estonian") + "\" (missed " + w.get("mistakes") + "x)")
            .collect(Collectors.joining(", "));

        var vocab = sentWordRepo.getVocabularyCollection(chatId);
        String recentList = vocab.stream()
            .limit(15)
            .map(w -> "\"" + w.get("word_value") + "\" (" + w.get("english") + ")")
            .collect(Collectors.joining(", "));

        int strongWords = ((Number) sentWordRepo.getLevelReadiness(chatId, level).get("strong")).intValue();
        int totalForLevel = wordBankService.getWordsForLevel(level).size();

        StringBuilder ctx = new StringBuilder();
        ctx.append("CEFR Level: ").append(level).append("\n");
        ctx.append("Native language: ").append(nativeLang).append("\n");
        ctx.append("Streak: ").append(streak).append(" days\n");
        ctx.append("Vocabulary: ").append(seen).append(" seen, ").append(mastered).append(" mastered\n");
        ctx.append("Level progress: ").append(strongWords).append("/").append(totalForLevel).append(" strong words\n");
        ctx.append("Quiz performance: ").append(totalQuizzes).append(" quizzes, avg ").append(avgPct).append("%\n");
        if (!weakList.isEmpty()) ctx.append("Weak words (prioritize these): ").append(weakList).append("\n");
        if (!missedList.isEmpty()) ctx.append("Most missed in quizzes: ").append(missedList).append("\n");
        if (!recentList.isEmpty()) ctx.append("Recently learned: ").append(recentList).append("\n");
        return ctx.toString();
    }
}
