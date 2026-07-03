package io.flashcard.service;

import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);
    private static final String CACHE_VERSION = "v4";
    private static final int TTS_TIMEOUT_SECONDS = 8;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 5 * 60 * 1000;

    private final AppProperties appProperties;
    private final DiskCacheService diskCache;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    public TtsService(AppProperties appProperties, DiskCacheService diskCache,
                      HttpClient httpClient, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.diskCache = diskCache;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return System.currentTimeMillis() >= circuitOpenUntil.get();
    }

    public byte[] synthesizeSpeech(String word, String sentence, String voiceName) {
        // Circuit breaker — skip TTS if it's been failing
        if (!isAvailable()) {
            log.debug("[tts] Circuit open, skipping TTS for \"{}\" (cooldown until {}ms from now)",
                word, circuitOpenUntil.get() - System.currentTimeMillis());
            return null;
        }

        String voice = voiceName != null ? voiceName : appProperties.getTtsSpeaker();
        String text = sentence != null && !sentence.equals(word) ? word + ". ... " + sentence : word;
        String cacheKey = CACHE_VERSION + "\0" + text + "\0" + voice;

        byte[] cached = diskCache.getCachedBuffer("tts", cacheKey, "ogg", DiskCacheService.TTS_TTL_MS);
        if (cached != null) {
            log.debug("[tts] Cache hit for \"{}\" ({})", word, voice);
            return cached;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("text", text, "speaker", voice, "speed", 0.85));
        } catch (Exception e) {
            log.error("[tts] Failed to serialize request body: {}", e.getMessage());
            return null;
        }

        try {
            long start = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/v2"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(TTS_TIMEOUT_SECONDS))
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("[tts] API error {} for \"{}\": {}", response.statusCode(), word,
                    new String(response.body()).substring(0, Math.min(200, response.body().length)));
                recordFailure();
                return null;
            }

            byte[] wavBuffer = response.body();
            byte[] audio = convertWavToOgg(wavBuffer);

            consecutiveFailures.set(0);
            diskCache.setCachedBuffer("tts", cacheKey, "ogg", audio);
            log.info("[tts] Synthesized \"{}\" in {}ms ({}bytes)", word, System.currentTimeMillis() - start, audio.length);

            return audio;
        } catch (Exception e) {
            log.warn("[tts] Failed for \"{}\": {}", word, e.getMessage());
            recordFailure();
            return null;
        }
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntil.set(System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS);
            log.warn("[tts] Circuit breaker OPEN — {} consecutive failures, skipping TTS for 5 minutes", failures);
        }
    }

    private byte[] convertWavToOgg(byte[] wavBuffer) {
        String id = UUID.randomUUID().toString().substring(0, 12);
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path wavFile = tmpDir.resolve("tts-" + id + ".wav");
        Path oggFile = tmpDir.resolve("tts-" + id + ".ogg");

        try {
            Files.write(wavFile, wavBuffer);
            ProcessBuilder pb = new ProcessBuilder("ffmpeg",
                "-i", wavFile.toString(),
                "-af", String.join(",",
                    "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-40dB",
                    "asetpts=PTS-STARTPTS",
                    "aresample=out_sample_rate=48000",
                    "highpass=f=80",
                    "lowpass=f=12000",
                    "equalizer=f=3000:t=q:w=2.0:g=1.5",
                    "acompressor=threshold=0.1:ratio=2:attack=10:release=150:makeup=1",
                    "afade=t=in:d=0.02",
                    "loudnorm=I=-16:TP=-1.5:LRA=11"),
                "-c:a", "libopus",
                "-b:a", "32k",
                "-application", "voip",
                "-vbr", "on",
                "-compression_level", "10",
                "-frame_duration", "20",
                "-ar", "48000",
                "-y", oggFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("[tts] ffmpeg exited with code {}, returning raw WAV", exitCode);
                return wavBuffer;
            }
            return Files.readAllBytes(oggFile);
        } catch (Exception e) {
            log.warn("[tts] ffmpeg conversion failed: {}, returning raw WAV", e.getMessage());
            return wavBuffer;
        } finally {
            try { Files.deleteIfExists(wavFile); } catch (IOException ignored) {}
            try { Files.deleteIfExists(oggFile); } catch (IOException ignored) {}
        }
    }
}
