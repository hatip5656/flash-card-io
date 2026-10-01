package io.flashcard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import io.flashcard.model.Podcast;
import io.flashcard.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PodcastService {

    private static final Logger log = LoggerFactory.getLogger(PodcastService.class);

    // Each part is a separate Gemini call → separate TTS call → concatenated at the end
    private static final String PART_GRAMMAR = """
            You are a language teacher creating an AUDIO podcast segment about Estonian grammar.
            The learner's native language is %s. Speak in their native language for explanations.

            LEARNER PROFILE:
            %s

            Pick ONE grammar topic relevant to the learner's weak/recent words.

            Generate a JSON array of segments teaching this grammar point with examples.
            Each segment: {"text": "...", "language": "%s" or "et", "pause_after_ms": int}

            Include:
            - Brief intro of the grammar rule (native lang)
            - 2-3 Estonian example sentences showing the rule, using the learner's weak words where possible
            - Translation of each example (native lang)
            - Pause after each Estonian sentence for the listener to repeat

            RULES:
            - 8-12 segments max
            - Keep each text SHORT: 1 sentence
            - Estonian: spell out numbers, max 12 words per sentence
            - Adjust complexity to the learner's CEFR level and quiz performance
            - Return ONLY valid JSON array, no markdown, no extra text
            - Do NOT mention the learner's stats/streak in the audio
            - IMPORTANT: In non-Estonian segments, wrap Estonian words with <<>> markers
              Example: "<<jõulud>> kelimesi Noel demektir" or "The word <<jõulud>> means Christmas"
              This helps TTS pronounce Estonian words correctly in other languages.
            - MUST end with ]
            """;

    private static final String PART_VOCABULARY = """
            You are a language teacher creating an AUDIO podcast segment teaching Estonian vocabulary.
            The learner's native language is %s. Speak in their native language for explanations.

            LEARNER PROFILE:
            %s

            Choose 4-6 words from the learner's weak/missed words to teach.

            Generate a JSON array of segments. For EACH word:
            1. Say the Estonian word (et)
            2. Pause 2000ms
            3. Give the meaning (native lang)
            4. Use it in a natural Estonian sentence (et)
            5. Translate the sentence (native lang)
            6. Pause 1500ms

            Each segment: {"text": "...", "language": "%s" or "et", "pause_after_ms": int}

            RULES:
            - Keep each text SHORT: 1 sentence
            - Estonian: spell out numbers, max 12 words per sentence
            - Prioritize words the learner struggles with most
            - Return ONLY valid JSON array, no markdown, no extra text
            - Do NOT mention the learner's stats/streak in the audio
            - IMPORTANT: In non-Estonian segments, wrap Estonian words with <<>> markers
              Example: "<<jõulud>> kelimesi Noel demektir" or "The word <<jõulud>> means Christmas"
              This helps TTS pronounce Estonian words correctly in other languages.
            - MUST end with ]
            """;

    private static final String PART_PRACTICE = """
            You are a language teacher creating an AUDIO podcast practice segment.
            The learner's native language is %s. Speak in their native language for instructions.

            LEARNER PROFILE:
            %s

            Create fill-in-the-blank and translation exercises using the learner's weak/missed words.

            Generate a JSON array of segments with:
            - Prompt in native lang: "How do you say X in Estonian?" or "Complete: Ma ___ raamatut"
            - Pause 3000ms for the listener to think
            - Give the answer in Estonian (et)
            - Pause 1000ms
            - Repeat for 3-4 exercises

            Each segment: {"text": "...", "language": "%s" or "et", "pause_after_ms": int}

            RULES:
            - 8-10 segments max
            - Keep each text SHORT: 1 sentence
            - Return ONLY valid JSON array, no markdown, no extra text
            - IMPORTANT: In non-Estonian segments, wrap Estonian words with <<>> markers
              Example: "<<raamat>> kelimesini soyleyin" or "How do you say <<raamat>>?"
            - MUST end with ]
            """;

    private final GeminiService geminiService;
    private final PodcastRepository podcastRepo;
    private final SentWordRepository sentWordRepo;
    private final SubscriberRepository subscriberRepo;
    private final QuizRepository quizRepo;
    private final ActivityRepository activityRepo;
    private final WordBankService wordBankService;
    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Path podcastDir;

    public PodcastService(GeminiService geminiService, PodcastRepository podcastRepo,
                          SentWordRepository sentWordRepo, SubscriberRepository subscriberRepo,
                          QuizRepository quizRepo, ActivityRepository activityRepo,
                          WordBankService wordBankService,
                          AppProperties appProperties, HttpClient httpClient,
                          ObjectMapper objectMapper,
                          @org.springframework.beans.factory.annotation.Value("${CACHE_DIR:/app/cache}") String cacheDir) {
        this.geminiService = geminiService;
        this.podcastRepo = podcastRepo;
        this.sentWordRepo = sentWordRepo;
        this.subscriberRepo = subscriberRepo;
        this.quizRepo = quizRepo;
        this.activityRepo = activityRepo;
        this.wordBankService = wordBankService;
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.podcastDir = Path.of(cacheDir, "podcasts");
        try { Files.createDirectories(podcastDir); } catch (Exception e) {
            log.warn("[podcast] Could not create podcast dir: {}", e.getMessage());
        }
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
        podcast.setDescription("Grammar, vocabulary and practice");

        String podcastId = podcastRepo.create(podcast);

        Thread.startVirtualThread(() -> generateAsync(podcastId, chatId, level));

        return podcastId;
    }

    private void generateAsync(String podcastId, long chatId, String level) {
        try {
            podcastRepo.updateStatus(podcastId, "generating", null);

            var prefs = subscriberRepo.getPreferences(chatId);
            String nativeLang = prefs != null ? prefs.getNativeLanguage() : "english";
            String langCode = "turkish".equals(nativeLang) ? "tr" : "en";

            String learnerContext = buildLearnerContext(chatId, level);

            log.info("[podcast] {} Generating parts for user {} ({}, native={})", podcastId, chatId, level, nativeLang);

            // Generate each part separately, synthesize, collect results
            List<PartResult> parts = new ArrayList<>();

            log.info("[podcast] {} Part 1: Grammar", podcastId);
            var grammar = generatePart(podcastId, "grammar",
                String.format(PART_GRAMMAR, nativeLang, learnerContext, langCode));
            if (grammar != null) parts.add(grammar);

            log.info("[podcast] {} Part 2: Vocabulary", podcastId);
            var vocab = generatePart(podcastId, "vocabulary",
                String.format(PART_VOCABULARY, nativeLang, learnerContext, langCode));
            if (vocab != null) parts.add(vocab);

            log.info("[podcast] {} Part 3: Practice", podcastId);
            var practice = generatePart(podcastId, "practice",
                String.format(PART_PRACTICE, nativeLang, learnerContext, langCode));
            if (practice != null) parts.add(practice);

            if (parts.isEmpty()) {
                podcastRepo.updateStatus(podcastId, "failed", "All parts failed to generate");
                return;
            }

            // Concatenate WAV files and merge timings with offset adjustment
            byte[] combined = concatenateWav(parts.stream().map(PartResult::audio).toList());
            List<Map<String, Object>> allTimings = mergeTimings(parts);

            // Save audio + timings
            String filename = podcastId + ".wav";
            Path audioPath = podcastDir.resolve(filename);
            Files.write(audioPath, combined);

            String timingsJson = objectMapper.writeValueAsString(allTimings);
            String title = generateTitle(allTimings);

            int durationSeconds = estimateDuration(combined);
            log.info("[podcast] {} Complete: {} parts, {} subtitles, ~{}s, title={}",
                podcastId, parts.size(), allTimings.size(), durationSeconds, title);

            podcastRepo.updateReady(podcastId, timingsJson, filename, durationSeconds, title);

        } catch (Exception e) {
            log.error("[podcast] Generation failed for {}: {}", podcastId, e.getMessage(), e);
            podcastRepo.updateStatus(podcastId, "failed", e.getMessage());
        }
    }

    private record PartResult(byte[] audio, List<Map<String, Object>> timings) {}

    private PartResult generatePart(String podcastId, String partName, String prompt) {
        try {
            String scriptJson = geminiService.chat(prompt, List.of(), "", 4096);
            if (scriptJson == null || scriptJson.isBlank()) {
                log.warn("[podcast] {} {} - Gemini returned null", podcastId, partName);
                return null;
            }

            scriptJson = cleanJson(scriptJson);
            List<?> segments = objectMapper.readValue(scriptJson, List.class);
            if (segments.isEmpty()) {
                log.warn("[podcast] {} {} - 0 segments", podcastId, partName);
                return null;
            }

            log.info("[podcast] {} {} - {} segments, sending to TTS", podcastId, partName, segments.size());
            var result = callTtsPodcastApi(scriptJson);
            if (result == null) {
                log.warn("[podcast] {} {} - TTS failed", podcastId, partName);
                return null;
            }

            log.info("[podcast] {} {} - OK ({} bytes, {} timings)", podcastId, partName,
                result.audio.length, result.timings.size());
            return result;

        } catch (Exception e) {
            log.warn("[podcast] {} {} - Error: {}", podcastId, partName, e.getMessage());
            return null;
        }
    }

    private String cleanJson(String raw) {
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        return repairTruncatedJson(json);
    }

    private String repairTruncatedJson(String json) {
        if (json.endsWith("]")) return json;
        int lastBrace = json.lastIndexOf('}');
        if (lastBrace <= 0) return json;
        String repaired = json.substring(0, lastBrace + 1).strip();
        if (repaired.endsWith(",")) {
            repaired = repaired.substring(0, repaired.length() - 1);
        }
        repaired += "]";
        log.warn("[podcast] Repaired truncated JSON (cut at position {})", lastBrace);
        return repaired;
    }

    @SuppressWarnings("unchecked")
    private PartResult callTtsPodcastApi(String scriptJson) throws JsonProcessingException {
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
                String errBody = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                log.error("[podcast] TTS API returned {}: {}", response.statusCode(),
                    errBody.substring(0, Math.min(500, errBody.length())));
                return null;
            }

            // Parse timings from header
            List<Map<String, Object>> timings = List.of();
            String timingsHeader = response.headers().firstValue("X-Segment-Timings").orElse(null);
            if (timingsHeader != null) {
                timings = objectMapper.readValue(timingsHeader, List.class);
            }

            return new PartResult(response.body(), timings);
        } catch (Exception e) {
            log.error("[podcast] TTS API call failed: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> mergeTimings(List<PartResult> parts) {
        List<Map<String, Object>> merged = new ArrayList<>();
        int offsetMs = 0;

        for (int p = 0; p < parts.size(); p++) {
            var part = parts.get(p);

            for (var timing : part.timings) {
                Map<String, Object> adjusted = new LinkedHashMap<>(timing);
                adjusted.put("start_ms", ((Number) timing.get("start_ms")).intValue() + offsetMs);
                adjusted.put("end_ms", ((Number) timing.get("end_ms")).intValue() + offsetMs);
                merged.add(adjusted);
            }

            // Offset = duration of this part's audio + 1s silence gap between parts
            int partDuration = estimateDuration(part.audio) * 1000;
            if (partDuration <= 0 && !part.timings.isEmpty()) {
                // Fallback: use last timing end_ms
                partDuration = ((Number) part.timings.get(part.timings.size() - 1).get("end_ms")).intValue();
            }
            offsetMs += partDuration;
            if (p < parts.size() - 1) {
                offsetMs += 1000; // 1s silence between parts
            }
        }

        return merged;
    }

    private String generateTitle(List<Map<String, Object>> timings) {
        try {
            String content = timings.stream()
                .map(t -> "[" + t.get("language") + "] " + t.get("text"))
                .collect(Collectors.joining("\n"));

            String title = geminiService.chat(
                "Here is a podcast lesson transcript:\n" + content +
                "\n\nGenerate a short, descriptive title (max 6 words) for this lesson. " +
                "Return ONLY the title text, nothing else.",
                List.of(), "");

            if (title != null && !title.isBlank()) {
                return title.strip().replaceAll("^\"|\"$", "");
            }
        } catch (Exception e) {
            log.warn("[podcast] Title generation failed: {}", e.getMessage());
        }
        return "Estonian Lesson";
    }

    /**
     * Concatenate multiple WAV files (same sample rate, 16-bit mono) into one.
     * Strips headers from all but the first, recalculates sizes.
     */
    private byte[] concatenateWav(List<byte[]> wavParts) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Write header from first file, we'll fix sizes at the end
        byte[] first = wavParts.get(0);
        out.write(first);

        // Append raw PCM data from remaining files (skip 44-byte WAV header)
        for (int i = 1; i < wavParts.size(); i++) {
            byte[] part = wavParts.get(i);
            if (part.length > 44) {
                // Add 1 second of silence between parts (24000 samples * 2 bytes)
                out.write(new byte[48000]);
                out.write(part, 44, part.length - 44);
            }
        }

        byte[] combined = out.toByteArray();

        // Fix RIFF size (file size - 8)
        int riffSize = combined.length - 8;
        combined[4] = (byte) (riffSize & 0xFF);
        combined[5] = (byte) ((riffSize >> 8) & 0xFF);
        combined[6] = (byte) ((riffSize >> 16) & 0xFF);
        combined[7] = (byte) ((riffSize >> 24) & 0xFF);

        // Fix data chunk size (file size - 44)
        int dataSize = combined.length - 44;
        combined[40] = (byte) (dataSize & 0xFF);
        combined[41] = (byte) ((dataSize >> 8) & 0xFF);
        combined[42] = (byte) ((dataSize >> 16) & 0xFF);
        combined[43] = (byte) ((dataSize >> 24) & 0xFF);

        return combined;
    }

    private int estimateDuration(byte[] wavData) {
        if (wavData.length < 44) return 0;
        int sampleRate = (wavData[24] & 0xFF) | ((wavData[25] & 0xFF) << 8)
                       | ((wavData[26] & 0xFF) << 16) | ((wavData[27] & 0xFF) << 24);
        int dataSize = (wavData[40] & 0xFF) | ((wavData[41] & 0xFF) << 8)
                     | ((wavData[42] & 0xFF) << 16) | ((wavData[43] & 0xFF) << 24);
        if (sampleRate <= 0) return 0;
        return dataSize / (sampleRate * 2);
    }

    public Optional<Podcast> getStatus(String podcastId) {
        return podcastRepo.findById(podcastId);
    }

    public Optional<String> getAudioFilename(String podcastId) {
        return podcastRepo.findById(podcastId)
            .filter(p -> "ready".equals(p.getStatus()))
            .map(Podcast::getAudioCacheKey);
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

        var wordCounts = sentWordRepo.getWordCounts(chatId);
        var quizStats = quizRepo.getQuizStats(chatId);

        int seen = ((Number) wordCounts.get("seen")).intValue();
        int mastered = ((Number) wordCounts.get("mastered")).intValue();
        int totalQuizzes = ((Number) quizStats.get("total")).intValue();
        int avgPct = (int) Math.round(((Number) quizStats.get("avg_pct")).doubleValue());

        var weakWords = sentWordRepo.getWeakWords(chatId, 10);
        String weakList = weakWords.stream()
            .map(w -> "\"" + w.get("word_value") + "\" (" + w.get("english") + ", ease=" + String.format("%.1f", ((Number) w.get("ease_factor")).doubleValue()) + ")")
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
        ctx.append("Vocabulary: ").append(seen).append(" seen, ").append(mastered).append(" mastered\n");
        ctx.append("Level progress: ").append(strongWords).append("/").append(totalForLevel).append(" strong words\n");
        ctx.append("Quiz performance: ").append(totalQuizzes).append(" quizzes, avg ").append(avgPct).append("%\n");
        if (!weakList.isEmpty()) ctx.append("Weak words (prioritize these): ").append(weakList).append("\n");
        if (!missedList.isEmpty()) ctx.append("Most missed in quizzes: ").append(missedList).append("\n");
        if (!recentList.isEmpty()) ctx.append("Recently learned: ").append(recentList).append("\n");
        return ctx.toString();
    }
}
